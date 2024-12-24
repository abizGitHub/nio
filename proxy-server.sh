mvn clean compile
#================ start backend server on 9101 ==========================
mvn spring-boot:run -Dspring-boot.run.arguments="9101" > back_9101.log &
#======================== start proxy ===========================
mvn exec:java -Dexec.mainClass="proxy.ProxyServerKt" &
#==================== test by calling port 8080 =========================
 ##  sleep 15
 ##  echo "start testing .."
 ##  for i in {1..500}; do
 ##      ## each call is supposed to have delay of 555.ms to respond.
 ##      http GET ":8080/delay/555" >> out.log &
 ##      if [ $((i % 50)) -eq 17 ]; then
 ##        echo "sleep .."
 ##        sleep 1
 ##        echo "resume"
 ##      fi
 ##  done
 ##  sleep 10
 ##  kill $(lsof -t -i :9101)
 ##  kill $(lsof -t -i :8080)
 ##  wc out.log




