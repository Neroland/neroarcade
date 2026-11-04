# Support dumps

Players run `/nsarcdebug dump` after reproducing a problem and send the resulting
`neroarcade-debug/support-*.nad` file from their game directory to the maintainer.
No operator permission or upload service is needed. `/nsarcdebug` explains the data collected.

Reports contain mod and loader versions (including Minecraft through the mod list), Java version,
maximum heap, screen range, renderer counters, seated match phase and up to 16 recent handled
faults: exception class plus up to eight NeroArcade class/method/line locations. They contain no
exception messages, raw logs, file contents, chat, cartridge titles, player IDs or world coordinates.
Fault metadata stays in memory until the process exits, independently of Sentry opt-out.
Dedicated-server faults are not fetched by this client command. Use existing server error reporting
for those. Reports are diagnostics, not deterministic match replays or proof of what happened.

## Read a report

Compile the offline tool from the repository root with JDK 21 or newer:

```powershell
javac -d build/support-tool common/src/main/java/za/co/neroland/neroarcade/diagnostics/DebugEncryption.java tools/DebugDumpTool.java
java -cp build/support-tool DebugDumpTool decrypt "$env:USERPROFILE/.neroarcade-support/support.private.der" "C:/path/support-example.nad" "C:/path/report.txt"
```

The output must not already exist. Decryption authenticates the whole envelope before writing
plaintext. The file format is versioned (`NAD1`), RSA-3072 OAEP SHA-256 / MGF1 SHA-256 wrapping a
fresh AES-256 key, and AES-GCM with a fresh 96-bit nonce and authenticated header.

Only `common/src/main/resources/neroarcade-support-public.der` belongs in the mod/repository.
The private key is stored outside the repository in the maintainer's `.neroarcade-support` directory.
Back it up securely: losing it makes reports for this public key unreadable. Anyone who obtains
the private key can read those reports. Encryption protects the saved file; it cannot prevent a
player inspecting or modifying their own running client. Do not treat a dump as authenticated evidence.

To rotate keys, generate into new paths (the tool refuses overwrites), then replace the bundled
public key deliberately. Retain the old private key for reports from older releases:

```text
java -cp build/support-tool DebugDumpTool keygen <private-path-outside-repo> <new-public-path>
```

At most one successful dump per minute and ten saved dumps per game directory are allowed.
Players delete reports manually when no longer needed; they are not part of a server's player-data
export or erasure because they contain no player-keyed data. Delete received reports and decrypted
copies when the support issue is resolved. Nothing is uploaded automatically.
