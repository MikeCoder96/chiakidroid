# chiakidroid
Official repo: https://git.sr.ht/~thestr4ng3r/chiaki

So, here the things done on the android app:

* Touchpad Controller now fully worked
* Disable/Enable on-screen touch commands (Find it on option window)
* Diasable/Enable touchscreen in fullscreen (Useful if you are on docking like Kishi V2, also find it on option window)
* Controller Vibration enabled
* Adaptive Trigger (Incoming)

Download on Relase Page (Tested and 100% working but not updated) or artifacts in Actions (Not tested but latest update) 

# HOW TO CALL THE OVERLAY (This should be work on official app too)

Find out that the overlay can be called, right now, only when you swipe to call the navigation bar (back button, home button and windows button).
This is because the overlay is called only when the method onSystemUiVisibilityChange is called. I will figure it out how to change it and if it is necessary.
Video: https://www.reddit.com/r/remoteplay/comments/12mcd0r/chiaki_android_with_full_implementation_of/

## Screenshots

Setting page:

![image](https://user-images.githubusercontent.com/50410305/233785296-bf6e93a8-8434-49ac-9592-eeeae3ab587a.png)

Hide/Show on screen buttons:

![image](https://user-images.githubusercontent.com/50410305/233785306-39392e24-62ab-4dc8-a7c4-73077433c03d.png)

Hide/Show touchpad on screen:

![image](https://user-images.githubusercontent.com/50410305/233785321-573d616f-991e-4eff-903b-c954de8bc427.png)

Builded with Android Studio on WSL2 (Kali-distro) 
