# DIYGPSTracker
Android app to log GPS data to your own Google Forestore back-end.
You install the Logger app (this app) to the device which is attached to the asset to be tracked.
There's a companion app which is the Controller and Viewer for the logger app and the back-end.

The main purpose is that I don't store or even touch any of your data. You just have to spin out your own Firebase project with your Firestore back-end, and you'll control your own data. This eco-system supports strict data control (only specified users can record or view), multiple assets to track, and ad-hoc geo-fencing (think about alarming or disarming your car).

The intended use:

0. Create Firebase project with Firestore back-end enabled. This is where your data will be stored, you manage it. The app developer won't receive any of your data.
1. Supply the `Project ID`, `App ID` and `API Key` of your project in the settings.
2. It's your choice if the Tracker, the Manager, or both, or neither will use `Google` or Anonymous authentication. You can choose the authentication mode in the apps. In case you select to use `Google` authentication on any of them, you'll need to enable that for your `Firebase` project.
3. You have the freedom to formulate your own Firestore rules.

My use-case:

* I'll use the apps for theft recovery measures.
* The tracker devices will be resgitered under my account as child phones and I'll use [Google Family Link](https://families.google.com/familylink/) as a fall-back plan in case the tracker app would stop working. This will also ensure that in case of a failed recovery, the device won't provide any personal information since my `Google` account won't be present on it.
* The tracker device will be hidden so it won't be easy to access its UI. Therefore if the app stops working for some reason, it'll first be restarted itself. To help that, the tracker app should be added to the startup application list, and user has to make sure that the battery saving measures are not applied to it.
* To help possibly auto-restart I'll use Anonymous login for the tracker application, but surely will use Googel login for the manager application and will configure the database rules keeping that in mind (also towards leats privilege possible).
* I'm using Google Fi data SIM for the tracker device, that would provide tracking with $0 monthly cost.
* There's a [22-Catch if you'd want to register a Google Family Link device with a data only SIM](https://support.google.com/fi/thread/38811153) 
