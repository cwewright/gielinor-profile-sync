Gielinor Profile Sync developer preview
=======================================

PRIVATE/LOCAL VALIDATION PACKAGE - DO NOT REDISTRIBUTE

This full package contains third-party RuneLite/runtime JARs. Redistribution
rights for the injected client and rlicn native payload are not established by
their published artifact metadata. Public installers must use the verified-
download bootstrap instead of embedding this package.

This temporary package starts a separate RuneLite development client with
Gielinor Profile Sync loaded. It does not modify or add files to the normal
RuneLite client.

Requirements
------------

- Windows x64
- Java 11. The Sailor's Log installer supplies a private Java 11 runtime.
- RuneLite launcher 2.6.3 or newer for the one-time Jagex Account setup.

Start the preview
-----------------

Run Launch-GielinorProfileSyncPreview.cmd. The launcher refuses Java versions
other than Java 11 and uses the isolated RuneLite profile named
gielinor-profile-sync-preview.

Jagex Accounts
--------------

Follow RuneLite's official "Using Jagex Accounts" guide. The exact temporary
client argument is:

    --insecure-write-credentials

Add it through RuneLite (configure), launch official RuneLite once from the
Jagex Launcher, and then remove the argument. Never share or upload the
credentials.properties file. Delete that file when this development preview is
no longer needed, as described by RuneLite's guide:

https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts

The preview itself starts with:

    --developer-mode --debug --profile gielinor-profile-sync-preview

Distribution integrity
----------------------

Preview/PREVIEW-MANIFEST.json records the exact RuneLite version and SHA-256
hash of every application and dependency JAR. Dependencies remain separate,
unmodified JARs. Project and dependency legal material is under Preview/legal.
