- New functionality:
    * Now, when you select a command from the dropdown menu, a panel will appear
        below displaying information about the command, and a button to send the
        command.

- To run: java -jar MyceliumHub-Phase4.jar

- Test 0: Use "Select lexicon file" button to select "sample-lexicon-v2.json"
    - "Device information" pane should display all five attributes as before,
        but also the dropdown menu should populate with the commands.
    - Select "formFeed", and the panel should appear below containing a text
        label "Advance to next label", and a button below it titled
        "Send formFeed".

- Notes: There are four fundamental types of commands:
    Command
    Query
    Parameterized command
    Parameterized query

    Only the first is implemented for this demo, and it doesn't yet communicate
    with the device. The next demo, v4, will demonstrate actual communication.