Fork from:
https://github.com/planestraveler/git-starteam

Additional things:

1. Use gradle for dependency management
2. Use oneJar to make a portable artifact

How to build:

1. Clone repository
2. Copy your Starteam SDK (i.e. starteam100.jar) to lib/starteam.jar
3. Run: gradle -I init.gradle oneJar
4. Find the result under: syncronizer/build/libs/syncronizer-standalone.jar
