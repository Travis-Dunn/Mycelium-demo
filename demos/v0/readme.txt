- IntelliJ IDEA project setup and configuration
- version control set up using git. This is for this series of demos only -
    production code will be in its own repo on the intermet account
- Technology stack decisions implemented:
  * JSON format chosen for device lexicon files
  * Jackson library for JSON parsing
  * Swing (javax.swing) for GUI framework
  * Maven for build system and dependency management
- Basic functionality:
  * File picker with JSON filtering
  * JSON parsing of lexicon files
  * Protocol field detection and display

- To run: java -jar MyceliumHub-Phase1.jar

- Test 0: Use "Select lexicon file" button to select "sample-lexicon.json"
    - "Device information" pane should display the string "Protocol: USB"
- Test 1: Select "broken-sample-lexicon.json"
    - You should get a non-fatal dialog box indicating an inability to find the
        "protocol" field.
