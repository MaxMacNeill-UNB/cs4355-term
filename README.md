# cs4355-term Project

Max MacNeill, Eric Smith, Dexter Lyons

File integrity monitor
- Calculate file hashes and checks them to see if it’s been changed since the last check
- Coding language: Java
- Security concepts touched on SHA-256, integrity, tampering detection
- Mainly check script files (extension specified by user)
- Bring files from the web to input file
- Save version history/defaults to JSON file
- Get hashes from files and compare/look for changes from the JSON saved defaults
- Alert if there are any changes
- Have options to delete history/set new default version to compare and look for changes
