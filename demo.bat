set root=C:\Dev\CorDapps\LetterOfCredit\kotlin-source\build\nodes

cd /D %root%\issuing
start cmd /k  java -jar -Xms512m -Xmx1024m corda.jar

cd /D %root%\advising
start cmd /k  java -jar -Xms512m -Xmx1024m corda.jar

cd /D %root%\buyer
start cmd /k  java -jar -Xms512m -Xmx1024m corda.jar

cd /D %root%\seller
start cmd /k  java -jar -Xms512m -Xmx1024m corda.jar

cd /D %root%\centralbankofcorda
start cmd /k  java -jar -Xms512m -Xmx1024m corda.jar

cd /D %root%\controller
start cmd /k  java -jar -Xms512m -Xmx1024m corda.jar

cd /D %root%\issuing
start cmd /k  java -jar corda-webserver.jar

cd /D %root%\advising
start cmd /k  java -jar corda-webserver.jar

cd /D %root%\buyer
start cmd /k  java -jar corda-webserver.jar

cd /D %root%\seller
start cmd /k  java -jar corda-webserver.jar

cd /D %root%\centralbankofcorda
start cmd /k  java -jar corda-webserver.jar