# MedidorEnergia
EnergyMonitor for Android. This app count a red led pulse that is associated with consumption of energy on a governmental building.

EnergyMonitor is an app created to solve a problem related to the monitoring of energy consumption in the 15th Federal Highway Police 
in the State of Rio Grande do Norte.

The problem:
Due to the costs with the acquisition of current sensors and the difficulties in acquiring the bidding processes, one had the idea of 
obtaining the consumption information, directly from the meter of the local power company.
However, access to this meter was not possible due to seals placed by the company.
It was noticed that there was a red LED that pulsed according to consumption in Watts-Hour.

Solution:
Create an application that could be installed on a smartphone that performs digital image processing in real time that counts the
LED pulses of the energy meter.

Technologies used:
- Samsung Grand Prime Duos Smartphone
- OpenCV Libraries for Android
- Google Script for receiving data from the mobile
- Google Spreadsheets to consolidate the data.

The application is still under development, but it is already possible to count, sending via Google Scripts and Google SpreadSheets.

To do:
- Insert a SQLLite database to store the data before sending.
- Modify the send to use a WebService and a remote database (it was verified that the google worksheet is slow due to the amount of data).
- Adjustments in comments and methods used.
