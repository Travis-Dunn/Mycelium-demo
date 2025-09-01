- New functionality:
    * Program now has a dropdown menu that shows the commands that the lexicon
        file declares.

- To run: java -jar MyceliumHub-Phase2.jar

- Test 0: Use "Select lexicon file" button to select "sample-lexicon.json"
    - "Device information" pane should display all five attributes.
- Test 1: Select "broken-sample-lexicon.json"
    - You should see four of the five fields populated, but 'description' is
        left as '--' because the entire node is missing from the lexicon file.
