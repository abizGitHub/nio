mvn clean compile

#================== start 5 backend servers ==========================
for i in {1..5} ; do
  echo "backed-server started on 910$i"
  mvn spring-boot:run -Dspring-boot.run.arguments="910$i" > back_"910$i".log 2>&1 &
done
#===================== start load balancer ===========================
argz="LEAST_RESPONSE_TIME 8082 9101 9102 9103 9104 9105"
mvn exec:java -Dexec.mainClass="loadbalancer.LoadBalancerKt"  -Dexec.args="$argz" &
#================= test by calling port 8080 =========================
sleep 25
echo "start testing .."
  for i in {1..1000}; do
      ## each call is supposed to have delay of 3.ms to respond.
      http GET ":8082/delay/3" >> out.log &
      if [ $((i % 50)) -eq 17 ]; then
        echo "sleep .."
        sleep 1
        echo "resume"
      fi
  done
  ##
sleep 15
for i in {1..5} ; do
  kill $(lsof -t -i :"910$i")
  c=$(grep "nio-910$i" out.log | wc -l)
  echo "server 910$i is called $c times."
done
kill $(lsof -t -i :8082)




