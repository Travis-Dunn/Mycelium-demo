- New functionality:
    * Now, when you select the printText command, enter the text in the entry
    widget, and press the "print text" button, it will print the text.

- To run: java -jar MyceliumHub-Phase8.jar

- Test 0 pre-requisite: Same as before, the drivers still need to be replaced.
    However, if you are using the same PC and haven't changed anything, this
    step does not need to be repeated.

- Test 0: Same as before, select the printText command from the dropdown, enter
    something in the "text to print" widget, and press the "print label" button.
    It should print a label.

- Notes: I only tested this with short strings such as "Hello world". It may and
    may not work with longer strings. However, the priorities of this project
    are not to build a good interface for the LabelWriter 450, and as such I
    do not intend to refine the text printing function at the moment. The next
    few demos will focus on other things, such as the lexicon format and general
    software architecture, and connecting over serial to the XQ2.