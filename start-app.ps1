$env:JAVA_HOME='C:\Users\USER\.vscode\extensions\redhat.java-1.53.0-win32-x64\jre\21.0.10-win32-x86_64'
$env:Path="$env:JAVA_HOME\bin;C:\Users\USER\tools\apache-maven-3.9.9\bin;$env:Path"
Set-Location 'C:\Users\USER\Desktop\IAA_SACCOS'
& 'C:\Users\USER\tools\apache-maven-3.9.9\bin\mvn.cmd' -DskipTests spring-boot:run *> run.log
