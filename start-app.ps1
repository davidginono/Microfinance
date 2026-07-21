$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:Path="$env:JAVA_HOME\bin;C:\Users\USER\tools\apache-maven-3.9.9\bin;$env:Path"
Set-Location 'C:\Users\USER\Desktop\SACCOS_LMS'
& 'C:\Users\USER\tools\apache-maven-3.9.9\bin\mvn.cmd' -DskipTests spring-boot:run *> run.log
