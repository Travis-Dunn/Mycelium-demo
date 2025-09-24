- New functionality:
    * Now, there is a greyed out "Connect" button next to the "Select lexicon"
        button, which, when pressed, attempts to establish a connection via the
        device's protocol.
    * USB communication is implemented.

- To run: java -jar MyceliumHub-Phase5.jar

- Test 0 pre-requisite: Before Test 0, you need to replace the printer drivers.
        This is a temporary time-saving step that allows for faster development.
    - Run "zadig-2.9.exe"
    - Options>List all devices
    - From the top dropdown, select the Dymo LabelWriter 450. It is important
        that you select only this device - replacing drivers for other items
        could brick them.
    - There is a line with the label "Driver", a field diplaying the current
        driver, a colored arrow pointing to the right, and a dropdown menu, from
        left to right. From the dropdown menu, select
        "WinUSB (v6.1.7600.16385)".
    - Press "Reinstall driver", agree to any message boxes, and you may proceed
        to test 0.

- Test 0: Use "Select lexicon file" button to select "sample-lexicon.json"
    - The "Connect" button should no longer be greyed out.
    - A "Ready to connect" label should appear to the right of the "Connect"
        button.
    - Press "Connect". The label to the right should change to "Connected",
        in green text. Device agnostic fields should be updated, and the
        commands dropdown should no longer be greyed out.
    - Select "formFeed" from the commands dropdown, press "Send", and the
        printer should feed until the next tear perforation.

- Notes:
    I was only able to get this to work by replacing the DYMO driver with
    WinUSB. This is a temporary fix. Now that I know that all of the other
    pieces are working, I can figure out why it doesn't work with the DYMO
    drivers, and fix it. However, it is not yet clear whether I should work on
    that next, or leave that and work in another direction.