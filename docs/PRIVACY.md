# Vidbox privacy

Vidbox does not operate an account service, analytics backend, advertising SDK, or media relay server.

When you analyze a link, the app contacts that source and any servers its extraction/download process requires. Those services receive the network information ordinarily associated with a request, including your IP address. Thumbnail hosts receive thumbnail requests. Vidbox's media operations happen on the device; this does not make third-party sites private or exempt their terms.

The built-in browser opens pages directly in the app and keeps only the current page address and title in memory so you can reopen it; page content, cookies and browsing history are never copied into Vidbox's database and the WebView's own history is not persisted. Websites you browse see the same network information as any other visit.

Download history, metadata and settings stay in app storage. Source specifications (including original/thumbnail URLs that may contain signed tokens) are encrypted using Android Keystore AES-GCM. User-facing titles, filenames, progress, dates and content URIs remain in the local Room database. No cookies or account passwords are accepted. App backup is disabled.

Saved files go to MediaStore or the document provider/folder you choose. Other media apps can see published MediaStore files. A cloud-backed folder may upload files according to that provider's policy. Sharing deliberately grants the receiving app read access to the selected file. Clipboard content is read only when you tap Paste; source links shared to Vidbox require explicit analysis.

Foreground notifications display filenames and progress; consider lock-screen notification privacy settings. App diagnostic events contain only bounded identifiers, state/error codes, sizes and rates, not source URLs or raw extraction output.

You can remove history without deleting saved files, or delete a file and its entry together. Uninstalling removes app-private data and its encryption key; already-published media is not automatically deleted. Android force-stop and permission revocation may require reopening the app or selecting the download folder again.
