VM Computers
==========
Run virtual machines inside vanilla Minecraft with a Spigot server!
--------------------------------------------------

VM Computers is a Spigot plugin that tries to be similar to the VM Computers mod.  Except, this time we handle everything. It literally runs a virtual machine inside of Minecraft (versus the mod which your computer handled by running VirtualBox), so we dont want you to get too carried away. We've also limited the VMs to DOS, older Windows versions (Vista and older), and hopefully Linux soon. Network support is also coming soon -- and who know's! Maybe multiplayer DOS games will be possible soon.

Further Information
-----------
VM Computers is currently in the alpha development stage, so don't expect it to always work and be stable. That's why this resource isn't on Spigot yet. Beware of running this as it may crash your server. You may not want to run too many computers on one server either. I'm not responsible for your actions if your server crashes.

Compiling from Source
------
To compile VM Computers, you need JDK 8 or later, an internet connection, and preferably an IDE (IntelliJ is the best).

If you don't want to use an IDE, clone this repo and run `maven package`. You can find the compiled jar in the
pom.xml's `outputDirectory` (That's the directory where the plugin is supposed to go on my computer). Otherwise, clone this repo, setup
a project that uses existing resources, and configure maven to run `maven package`.

Binaries
------
You can download the jar files in the releases tab if you don't want to compile VM  Computers from source. Just keep in mind they won't always be up to date with the main (alpha) branch.

(c) 2021-2023 Anston Sorensen
