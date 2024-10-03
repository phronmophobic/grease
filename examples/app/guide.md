# iPhone Development Guide

This guide will provide the main steps for running clojure on your iPhone.

# Setup

## Download the beta to your phone

# Development

## Connecting via nrepl

Open the app and tap the start nrepl button. If you are connected to a
local wifi network, an nrepl server will start and display the
ip and port. It's technically possible to start an nrepl server
on a globally public IP address, but that is not currently supported.

## Using the nrepl

Once connected, the nrepl should work like any other nrepl with a few caveats.

### Uploading scripts

If a `:load-file` command is sent to the nrepl server, the file will be evaled
and copied to the phone's filesystem. In emacs, `:load-file` corresponds to
`M-x cider-load-buffer` or `C-c C-k`.

### Useful namespaces

An updated list of available namespaces can be found [here](https://github.com/phronmophobic/grease/blob/cf3207f92ae00ff351a4a65e391e62165a713602/src/com/phronemophobic/grease/ios.clj#L455).

Most of these namespaces are useful namespaces for writing clojure scripts like you would find in babashka or on the clojure JVM. However, there are a few namespaces included that are specifically helpful for iOS development.

`com.phronemophobic.objcjure`: is a fully capable objc DSL for calling platform APIs.
`com.phronemophobic.grease.ios`: has utilties and helper functions that might typically be needed when building an iOS app.
`membrane.ui`, `membrane.component`, `com.phronemophobic.grease.component`: Namespaces for building user interfaces.

Finally, there is an `app` namespace which is loaded by default.

The main function for building "widgets" is `app/show!`.

```
app/show!
[{:keys [view-fn on-close]}]
  Displays a new widget.

  The following keys are avilable:
  :view-fn (required): A function that takes no args and returns a view to be displayed.
              The view does not automatically repaint. Call repaint! to update the view.
  :on-close: A function that takes no args. Will be called when the app is closed.

  show! returns a map with the following keys:
  :repaint!: A function that takes no args. It will cause the app's view to be displayed.

  If a widget is currently open, it will be closed.
```

### Standalone scripts

Currently, each file is standalone and can only require the specific namespaces [listed](https://github.com/phronmophobic/grease/blob/cf3207f92ae00ff351a4a65e391e62165a713602/src/com/phronemophobic/grease/ios.clj#L455). However, `load-string` works as normal:

```clojure
(require '[babashka.fs :as fs]
         '[com.phronemophobic.grease.ios :as ios])
(load-string
 (String. (fs/read-all-bytes
           (fs/file (ios/scripts-dir) "gol.clj"))))
```

Support for projects and deps is planned for the future.

### Running local files

When the app is opened, a rudimentary file browser is shown. A local script can be run by navigating to the particular file and tapping the eval button. The script will be loaded and the `-main` function in that namespace will be called, if available.
