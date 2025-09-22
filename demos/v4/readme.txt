- New functionality:
    * Now, there is a greyed out "Connect" button next to the "Select lexicon"
        button, which, when pressed, attempts to establish a connection via the
        device's protocol.
    * USB communication is implemented.

- To run: java -jar MyceliumHub-Phase5.jar

- Test 0: Use "Select lexicon file" button to select "sample-lexicon.json"
    - The "Connect" button should no longer be greyed out.
    - A "Ready to connect" label should appear to the right of the "Connect"
        button.
    - See me for instructions on replacing the LabelWriter 450 driver.
    - Press "Connect". The label to the right should change to "Connected",
        in green text. Device agnostic fields should be updated, and the
        commands dropdown should no longer be greyed out.
    - Select "formFeed" from the commands dropdown, press "Send", and the
        printer should feed until the next tear perforation.

- Notes: There are four fundamental types of commands:
    I was only able to get this to work by replacing the DYMO driver with
    WinUSB. This is a temporary fix. Now that I know that all of the other
    pieces are working, I can attempt to make it work with the DYMO drivers.
    However, it is not yet clear whether I should work on that next, or leave
    that and work in another direction.