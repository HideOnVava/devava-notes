module com.devavaxp.notes {
    requires javafx.controls;
    requires javafx.web;
    requires jdk.jsobject;     // netscape.javascript.JSObject: Java calls into the editor's JavaScript
    requires java.desktop;     // the Documents folder (FileSystemView), the trash and "show in folder" (Desktop)
    requires com.google.gson;  // .notebook.json
    requires org.commonmark;
    requires org.commonmark.ext.gfm.tables;
    requires org.commonmark.ext.gfm.strikethrough;
    requires org.commonmark.ext.task.list.items;

    // JavaFX creates the Application reflectively.
    opens com.devavaxp.notes to javafx.graphics;
}
