# Contributing to BluBox 360

BluBox 360 is an early public alpha led by Macca and the BluBox team. Helpful
bug reports, compatibility results, documentation fixes, and focused source
changes are welcome.

## Before opening an issue

- Test the latest public alpha.
- Search open and closed issues for the same title or error.
- Use the correct issue form.
- Remove personal paths and account names from logs.
- Do not upload games, firmware, keys, copyrighted assets, or proprietary code.

## Source changes

1. Open an issue for a large behavior change before starting work.
2. Keep each change focused on one problem.
3. Preserve the existing Java 17 and Android API requirements.
4. Build with the steps in [BUILD.md](BUILD.md).
5. Test the changed path and describe the result in the pull request.
6. Keep signing keys, passwords, local SDK paths, and generated APKs out of git.

Contributions must be original work or code used under a compatible license.
List the source and license for imported work.

## Pull request checklist

- The change has a clear reason and a small reviewable scope.
- The release build completes.
- No game, firmware, key, private log, or signing material is included.
- User-facing behavior is documented.
- Relevant tests and device results are listed.
