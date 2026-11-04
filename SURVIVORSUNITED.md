# SurvivorsUnited 1.21.11 overlay

This branch tracks upstream's `1.21.11` while keeping the SurvivorsUnited changes in standalone source files and separately registered commands. Do **not** merge this branch into the upstream-tracking `1.21.11` branch; periodically merge `1.21.11` **into** `survivorsunited/1.21.11`.

## Commands

These commands are for **the 1.21.11 fork build only**. They do not exist in the older 0.25.8 server mod.

### Admin party management

Requires Minecraft game-master permission (OP level 2+) or console:

```mcfunction
/oparties admin info ArkadiBa
/oparties admin add ArkadiBa fungy_enjoyer
/oparties admin remove ArkadiBa fungy_enjoyer
/oparties admin invite ArkadiBa fungy_enjoyer
/oparties admin move fungy_enjoyer ArkadiBa
```

For `move`, the first argument is the **destination party owner**; i.e. the actual syntax is `/oparties admin move <destination-owner> <player>`. A player who owns another party cannot be moved until ownership is transferred. An ordinary `add` refuses players already in a party.

Admin `invite` sends a normal invitation (does not automatically join). Online players also see the accept command; pending invites can be viewed using `/oparties invites`.

### Player invitations

```mcfunction
/oparties invites
/oparties accept
/oparties accept ArkadiBa
/oparties decline ArkadiBa
```

The original `/oparties join <party-uuid>` remains supported; `accept` accepts a party owner name, exact default party name, UUID or (with no argument) the most recent invitation. Invite acceptance is visible even to members already in a party, but returns an explanatory error rather than changing existing membership.

### Scoped claim access

```mcfunction
/oclaims admin access grant ArkadiBa fungy_enjoyer full main
/oclaims admin access revoke ArkadiBa fungy_enjoyer full main
```

This modifies **ArkadiBa's main player claim config only**, *not* subclaims, party-owned claims or server-owned claims. It creates a custom `su-admin-access` player group and nests the previous full-access group to preserve existing party/ally access. Refuses to overwrite the main configuration if an unrelated admin has switched the active access group. For subclaims, use the existing owner-targeted `/opac player-config for ...` configuration commands until scoped subclaim management is implemented.

**Back up claim/party data before testing admin modifications.** The protection implementation should be tested with a non-OP account.

## Build and release

GitHub Actions workflow `.github/workflows/survivorsunited-fabric-1.21.11.yml`:
- Builds Fabric using Java 21 via `./gradlew :Fabric:build`.
- Fails unless exactly one distributable JAR is found in `Fabric/build/libs`.
- Packages the JAR, `SHA256SUMS.txt` and `README.txt` into a downloadable Actions artifact.
- On a successful push to `survivorsunited/1.21.11`, **attempts** to publish a versioned GitHub prerelease tagged `su-1.21.11-<commit>` (or attaches assets to a manually published release) when the repository Actions secret `SURVIVORSUNITED_RELEASE_TOKEN` is configured. The token needs repository Contents: write, and may need Workflows: write because the release target branch contains workflow changes not present on the default branch. Without the secret, release publication is skipped but the Actions build artifact remains downloadable. GitHub's built-in workflow token produced HTTP 403 on release creation even though the job requested Contents: write.
- Does not deploy to the production server or update minecraft-mods-manager automatically.

## Server verification checklist (Minecraft 1.21.11)

1. Install the new Fabric build and remove the old 0.25.8 jar from the server. Confirm only one OPAC jar is installed.
2. Restart a test server and verify `/oparties admin`, `/oparties invites`, and `/oclaims admin access` autocomplete for OPs; none of the admin commands must be visible/executable to regular players.
3. Create test parties for two players. Run `admin add` and verify direct membership, storage access and immediate synchronisation. Repeat with an offline/cached player, then restart.
4. Verify `admin add` rejects an existing member of another party; `admin move` works for non-owners, rejects party owners and full destination parties.
5. Verify `admin remove` rejects owners and that a removed player loses claim access. Test `admin invite` plus `invites`, `accept`, `decline`, ambiguity, and acceptance when already a party member.
6. With claims set to party or allied access, `access grant ... full main` should give only the named player access while continuing to allow existing party/allied users. `revoke` must restore restrictions. Verify unrelated players cannot access.
7. Verify an owner subclaim continues to follow its own rules, including when its configuration overrides main full access; verify server-forced claim configurations are not silently overridden.
8. Restart and repeat checks 3–7 to verify persistence. Inspect server log for administrative audit entries.

## Follow-up compatibility work

The implementation has not yet been exercised in a live Minecraft 1.21.11 server. Compile/build in CI and the acceptance checks above are gates before production deployment. Additional automated command-tree and integration tests are recommended, particularly for offline profile lookup, permission boundaries, and claim-group persistence.
