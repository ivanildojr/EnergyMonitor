package br.com.indicare.opencvandroid;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.script.model.ExecutionRequest;
import com.google.api.services.script.model.Operation;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static org.opencv.core.Core.FONT_HERSHEY_SCRIPT_SIMPLEX;
import static org.opencv.core.Core.extractChannel;


public class MainActivity extends Activity implements View.OnTouchListener, CameraBridgeViewBase.CvCameraViewListener2, EasyPermissions.PermissionCallbacks{

    private static final String TAG = "MainActivity";
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat                  mRgba;
    private Rect regiaoInteresse = new Rect(0,0,0,0);
    private Scalar mediaMax = Scalar.all(8);
    private boolean ledLigado = false;
    private boolean led_medidor_ant = true;
    private long contador = 0;
    public PowerManager.WakeLock wl;
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    private static final String PREF_ACCOUNT_NAME = "";
    GoogleAccountCredential mCredential;
    private static final String[] SCOPES = { "https://www.googleapis.com/auth/spreadsheets" };
//    private static final String[] SCOPES = { "https://www.googleapis.com/auth/drive" };

    Time ultimoTempoEnvio = null;
    long ultimoContadorEnvio = 0;
    long ultimaDiferencaTempoEnvio = 0;
    long ultimadiferancaContadorEnvio = 0;
    static final int INTERVALO_ENVIO_PLANILHA_TEMPO = 10;
    static final int INTERVALO_ENVIO_PLANILHA_PULSOS = 10;
    static final int MEDIA_IMAGEM_OFFSET = 10;
    static final double THRESHOLD_OFFSET = 0.8;
    int media_imagem_progress = 10;
    TextView contadorTxt = null;
    TextView txtOffset = null;
    static final String[] btn_txt = {"Inciar Processamento","Processando..."};
    boolean processando = false;
    AlertDialog.Builder builder;
    Handler h = new Handler();
    Runnable runnable;
    int delay = 10000; //um segundo

    IntentFilter ifilter;
    Intent batteryStatus;


    static {
        if(!OpenCVLoader.initDebug()){
            Log.d(TAG, "OpenCV não carregado!");
        }else{
            Log.d(TAG, "OpenCV carregado!");
        }
    }

    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                        Log.i(TAG, "SIM");
                        contador = 0;
                        contadorTxt.setText("0");
//                        Log.i(TAG, "Valor Contador: " + contador);
//                        Log.i(TAG, "Valor ContadorTxt: " + contadorTxt.getText());
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    //Não faça nada.
//                    Log.i(TAG, "Não");
                    break;
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.disableFpsMeter();

//        ArrayList<View> botoes = new ArrayList<View>();
//        botoes.add(findViewById(R.id.btnIniciar));
//
//        mOpenCvCameraView.addTouchables(botoes);

        contadorTxt = (TextView) findViewById(R.id.txtValorContador);
        contadorTxt.setText(String.valueOf(contador));

        txtOffset = (TextView) findViewById(R.id.txtOffset);
        txtOffset.setText(String.valueOf(media_imagem_progress));

        SeekBar sensibilidade = (SeekBar) findViewById(R.id.seekSensibilidade);
        sensibilidade.setProgress(MEDIA_IMAGEM_OFFSET);
        sensibilidade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                media_imagem_progress = seekBar.getProgress();
                /*Necessário para alterar o textView na sua própria Thread.*/
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txtOffset.setText(String.valueOf(media_imagem_progress));
                    }
                });
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        ultimoTempoEnvio = new Time(new Date().getTime());



        builder = new AlertDialog.Builder(this);

        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = this.registerReceiver(null, ifilter);


    }

    @Override
    protected void onStart() {
        super.onStart();
    }






    public void iniciarProcessamento(View view){
        Button disp = (Button) findViewById(R.id.btnIniciar);
        if(!processando) {
            disp.setText(btn_txt[1]);
            builder.setMessage("Deseja zerar o contador?").setPositiveButton("Sim", dialogClickListener)
                    .setNegativeButton("Não", dialogClickListener).show();
            processando = !processando;
        }else{
            disp.setText(btn_txt[0]);
            processando = !processando;
        }


    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    mOpenCvCameraView.enableView();
                    
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        h.postDelayed(new Runnable() {
            public void run() {
                if(processando){
                    Time t = new Time(new Date().getTime());
                    getResultsFromApi();
                    checkEnvio(t, contador);
                }
                runnable=this;

                h.postDelayed(runnable, delay);
            }
        }, delay);

    }

    public void onCameraViewStopped() {
        mRgba.release();
        h.removeCallbacks(runnable);
    }

    /*Verifica se já se acumularam os pulsos e o tempo para envio a planilha*/
    private boolean checkEnvio(Time t, long count){

        long diferencaTempo = (t.getTime() - ultimoTempoEnvio.getTime()) / 1000;
        long diferencaContador = count - ultimoContadorEnvio;

//        if(diferencaTempo >= INTERVALO_ENVIO_PLANILHA_TEMPO){
//            if(diferencaContador > INTERVALO_ENVIO_PLANILHA_PULSOS){
                ultimoContadorEnvio = count;
                ultimoTempoEnvio = t;
                ultimaDiferencaTempoEnvio = diferencaTempo;
                ultimadiferancaContadorEnvio = diferencaContador;
                return true;
//            }else{
//                return false;
//            }
//        }return false;
    }


    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();


        if(processando) {

            Mat gray = new Mat();
                /*Extrai o canal Vermelho*/
            extractChannel(mRgba, gray, 0);

                /*calcula o brilho máximo*/
            Core.MinMaxLocResult maximus = Core.minMaxLoc(gray);

            int threshold = (int) (maximus.maxVal * THRESHOLD_OFFSET);

                /*Define a regiao de interesse baseado no touch*/
            Mat regiaoInteresseImg = new Mat(gray, regiaoInteresse);

            Imgproc.threshold(regiaoInteresseImg, regiaoInteresseImg, threshold, 255, Imgproc.THRESH_BINARY);

            Scalar media = Core.mean(regiaoInteresseImg);

            if (media.val[0] < mediaMax.val[0] - media_imagem_progress) {
                ledLigado = false;
            } else {
                ledLigado = true;
            }
                /*Inserir a contagem atual a ser enviado para a ultima enviada e a diferenca do tempo*/
            if (!led_medidor_ant && ledLigado) {
                contador = contador + 1;

                /*Necessário para alterar o textView na sua própria Thread.*/
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        contadorTxt.setText(String.valueOf(contador));
                        txtOffset.setText(String.valueOf(media_imagem_progress));
                    }
                });


//                if (checkEnvio(new Time(new Date().getTime()), contador))
//                    getResultsFromApi();
            }

            mediaMax.val[0] = media.val[0];
            led_medidor_ant = ledLigado;

            //        Imgproc.putText(mRgba,"Média: " + media.val[0],new Point((mOpenCvCameraView.getWidth()/10)*8.5,(mOpenCvCameraView.getHeight()/10)*9),FONT_HERSHEY_SCRIPT_SIMPLEX,0.5,new Scalar(0,255,0),2);
            //        Imgproc.putText(mRgba,"Contador: " + contador,new Point((mOpenCvCameraView.getWidth()/10)*8.5,(mOpenCvCameraView.getHeight()/10)*9.5),FONT_HERSHEY_SCRIPT_SIMPLEX,0.5,new Scalar(0,255,0),2);


            regiaoInteresseImg.release();


            gray.release();


            if (regiaoInteresse.x > 0 && regiaoInteresse.y > 0)
                Imgproc.circle(mRgba, new Point(regiaoInteresse.x, regiaoInteresse.y), 20, new Scalar(255, 0, 0, 0), 2);
        }else{
            if (regiaoInteresse.x > 0 && regiaoInteresse.y > 0)
                Imgproc.circle(mRgba, new Point(regiaoInteresse.x, regiaoInteresse.y), 20, new Scalar(255, 0, 0, 0), 2);
        }



        return mRgba;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Coordenadas do Touch: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;



        regiaoInteresse = touchedRect;
//        Log.d(TAG, "Regiao de interesse - Height: " + regiaoInteresse.height + " Width: " + regiaoInteresse.width + " X: " + regiaoInteresse.x + " Y: " + regiaoInteresse.y);

        return true;
    }

    /**
     * Extend the given HttpRequestInitializer (usually a credentials object)
     * with additional initialize() instructions.
     *
     * @param requestInitializer the initializer to copy and adjust; typically
     *         a credential object.
     * @return an initializer with an extended read timeout.
     */
    private static HttpRequestInitializer setHttpTimeout(
            final HttpRequestInitializer requestInitializer) {
        return new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest httpRequest)
                    throws java.io.IOException {
                requestInitializer.initialize(httpRequest);
                // This allows the API to call (and avoid timing out on)
                // functions that take up to 6 minutes to complete (the maximum
                // allowed script run time), plus a little overhead.
                httpRequest.setReadTimeout(380000);
            }
        };
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            Log.d(TAG, "getResultsFromApi: "+"No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }


    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog

            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Log.d(TAG, "onActivityResult: "+ "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the Google Apps Script Execution API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.script.Script mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.script.Script.Builder(
                    transport, jsonFactory, setHttpTimeout(credential))
                    .setApplicationName("Google Apps Script Execution API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call Google Apps Script Execution API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }


        /**
         * Call the API to run an Apps Script function that returns a list
         * of folders within the user's root directory on Drive.
         *
         * @return list of String folder names and their IDs
         * @throws IOException
         */
        private List<String> getDataFromApi()
                throws IOException, GoogleAuthException {
            // ID of the script to call. Acquire this from the Apps Script editor,
            // under Publish > Deploy as API executable.

            String scriptId = "";
            List<String> folderList = new ArrayList<String>();


            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            float batteryPct = level / (float)scale; /*Nivel da bateria*/




            List parametros = new ArrayList();
            parametros.add(0,ultimaDiferencaTempoEnvio);
            parametros.add(1,ultimadiferancaContadorEnvio);
            parametros.add(2,new Date().toString());
            parametros.add(3,contador);
            parametros.add(4,batteryPct);







            // Create an execution request object.
            ExecutionRequest request = new ExecutionRequest()
                    .setFunction("doPost").setParameters(parametros);

            // Make the request.
            Operation op =
                    mService.scripts().run(scriptId, request).execute();

            // Print results of request.
            if (op.getError() != null) {
                throw new IOException(getScriptError(op));
            }
            if (op.getResponse() != null &&
                    op.getResponse().get("result") != null) {
                // The result provided by the API needs to be cast into
                // the correct type, based upon what types the Apps Script
                // function returns. Here, the function returns an Apps
                // Script Object with String keys and values, so must be
                // cast into a Java Map (folderSet).
                Map<String, String> folderSet =
                        (Map<String, String>)(op.getResponse().get("result"));

                for (String id: folderSet.keySet()) {
                    folderList.add(
                            String.format("%s (%s)", folderSet.get(id), id));
                    Log.d(TAG, "RESPOSTA DO SCRIPT: " + String.format("%s (%s)", folderSet.get(id), id));
                }
            }

            return folderList;
        }

        /**
         * Interpret an error response returned by the API and return a String
         * summary.
         *
         * @param op the Operation returning an error response
         * @return summary of error response, or null if Operation returned no
         *     error
         */
        private String getScriptError(Operation op) {
            if (op.getError() == null) {
                return null;
            }

            // Extract the first (and only) set of error details and cast as a Map.
            // The values of this map are the script's 'errorMessage' and
            // 'errorType', and an array of stack trace elements (which also need to
            // be cast as Maps).
            Map<String, Object> detail = op.getError().getDetails().get(0);
            List<Map<String, Object>> stacktrace =
                    (List<Map<String, Object>>)detail.get("scriptStackTraceElements");

            java.lang.StringBuilder sb =
                    new StringBuilder("\nScript error message: ");
            sb.append(detail.get("errorMessage"));

            if (stacktrace != null) {
                // There may not be a stacktrace if the script didn't start
                // executing.
                sb.append("\nScript error stacktrace:");
                for (Map<String, Object> elem : stacktrace) {
                    sb.append("\n  ");
                    sb.append(elem.get("function"));
                    sb.append(":");
                    sb.append(elem.get("lineNumber"));
                }
            }
            sb.append("\n");
            return sb.toString();
        }





        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    Log.d(TAG, "onCancelled: "+"The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                Log.d(TAG, "onCancelled: "+"Request cancelled.");
            }
        }
    }
}
