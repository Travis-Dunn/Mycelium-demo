- The primary thing on display here is a lot of comments explaining how the
    newly added functionality works.
- New functionality:
    * Program now gets all of the device-agnostic fields from the lexicon file
        and displays them to the GUI.

- To run: java -jar MyceliumHub-Phase2.jar

- Test 0: Use "Select lexicon file" button to select "sample-lexicon.json"
    - "Device information" pane should display all five attributes.
- Test 1: Select "broken-sample-lexicon.json"
    - You should see four of the five fields populated, but 'description' is
        left as '--' because the entire node is missing from the lexicon file.
