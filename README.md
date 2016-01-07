# PrimoSync

Multi-server Economy Synchronization using Redis. 
No MySQL database required. Syncs data every [configurable] seconds (default 10). 

## Installation instructions:

1. Install redis-server on your server. ('sudo apt-get install redis-server' for ubuntu/debian)
2. Upload PrimoSync to every server you want to synchronize.
3. Start each server, and shut each server down again.
4. Edit the config files that were generated on each server. (make sure the redis information is correct)
5. Start the servers up again.
6. Enjoy.

