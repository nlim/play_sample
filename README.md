## Tuning the Play Framework 2.0

Play uses Akka to dispatch controller actions to Actors that map to a thread pool (the default Fork-Join Executor).
It is intended that these actions never block. Here's a quote from the documentation.

    Because of the way Play 2.0 works, action code must be as fast as possible (i.e. non blocking).
    So what should we return as result if we are not yet able to compute it?
    The response should be a future result!

So if your controller action does any intense computation or blocking io, then that hangs up a thread in the default
execution context from returning a response to a client that has its intense computation ready to serve!  The best
practice here is run the intense computation in another thread, and have the controller action thread (as part of
the default dispatcher) and have the controller action immediately return with an AsyncResult.  That way, the threads
in the default execution context can do what they are supposed to do, namely:

Return responses that are ready to the client!

If you read Link #1 at the end of this README for more information.

### Problems with the Java Promise API

According the Play Documentation, all of the actions of a controller are performed in the
default execution context (Thread Pool). Furthermore it says:

    When using the Java promise API, for most operations
    you do not get a choice as to which execution context
    will be used, the Play default execution context
    will always be used.

Man this is terrible!  In real world applications I want absolute control over the thread pool configuration
of every bit of blocking IO or CPU intensive operation.  I don't want to clog up the threads in my default
execution context, nor do I necessarily want to run all my intensive operations in the same thread pool.

To demonstrate the problems here, I ran the following test of this project in Apache Bench.

#### Java Promise API Results

If you look at `app/controllers/WebServices` I have a controller action called `javaBad`.
It uses the Java Promise API to running intense computation takes 30 milliseconds (sleeping).

    [~] $   /usr/local/Cellar/ab/2.4.3/bin/ab -n 2000 -c 50 http://localhost:9000/javaBad
    This is ApacheBench, Version 2.3 <$Revision: 1373084 $>
    Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
    Licensed to The Apache Software Foundation, http://www.apache.org/

    Benchmarking localhost (be patient)
    Completed 200 requests
    Completed 400 requests
    Completed 600 requests
    Completed 800 requests
    Completed 1000 requests
    Completed 1200 requests
    Completed 1400 requests
    Completed 1600 requests
    Completed 1800 requests
    Completed 2000 requests
    Finished 2000 requests

    Server Software:
    Server Hostname:        localhost
    Server Port:            9000

    Document Path:          /javaBad
    Document Length:        33 bytes

    Concurrency Level:      50
    Time taken for tests:   2.599 seconds
    Complete requests:      2000
    Failed requests:        0
    Write errors:           0
    Total transferred:      226000 bytes
    HTML transferred:       66000 bytes
    Requests per second:    769.39 [#/sec] (mean)
    Time per request:       64.987 [ms] (mean)
    Time per request:       1.300 [ms] (mean, across all concurrent requests)
    Transfer rate:          84.90 [Kbytes/sec] received

    Connection Times (ms)
                  min  mean[+/-sd] median   max
    Connect:        0    0   0.3      0       2
    Processing:    31   64  22.9     61     179
    Waiting:       31   64  22.9     61     179
    Total:         32   64  22.9     61     179

    Percentage of the requests served within a certain time (ms)
      50%     61
      66%     63
      75%     68
      80%     88
      90%     93
      95%    101
      98%    123
      99%    131
     100%    179 (longest request)

Even if I increase the default-dispatcher thread pool size, in this example (see below),
it doesn't change the fact that I've introduced blocking threads into the thread pool
responsible for returning Responses that are ready.  Apache Bench gives me similar results as above.

    play {
      akka {
        event-handlers = ["akka.event.Logging$DefaultLogger", "akka.event.slf4j.Slf4jEventHandler"]
        loglevel = WARNING
        actor {
            my-context {
                fork-join-executor {
                    parallelism-min = 600
                    parallelism-max = 600
                }
            }
            default-dispatcher {
                fork-join-executor {
                    parallelism-min = 600
                    parallelism-max = 600
                }
            }
        }
      }
    }

#### Scala Future API, explicitly defining the execution context.

If you look at `app/controllers/Application` I have a controller action called `good`, which fires
off a Scala Future to run the intense computation takes 30 milliseconds (sleeping), in an execution context
that I configure in the following way:

    play {
      akka {
        event-handlers = ["akka.event.Logging$DefaultLogger", "akka.event.slf4j.Slf4jEventHandler"]
        loglevel = WARNING
        actor {
            my-context {
                fork-join-executor {
                    parallelism-min = 600
                    parallelism-max = 600
                }
            }
         }
       }
    }

Running Apache Bench again

    [~] $   /usr/local/Cellar/ab/2.4.3/bin/ab -n 2000 -c 50 http://localhost:9000/good
    This is ApacheBench, Version 2.3 <$Revision: 1373084 $>
    Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
    Licensed to The Apache Software Foundation, http://www.apache.org/

    Benchmarking localhost (be patient)
    Completed 200 requests
    Completed 400 requests
    Completed 600 requests
    Completed 800 requests
    Completed 1000 requests
    Completed 1200 requests
    Completed 1400 requests
    Completed 1600 requests
    Completed 1800 requests
    Completed 2000 requests
    Finished 2000 requests


    Server Software:
    Server Hostname:        localhost
    Server Port:            9000

    Document Path:          /good
    Document Length:        13 bytes

    Concurrency Level:      50
    Time taken for tests:   1.378 seconds
    Complete requests:      2000
    Failed requests:        0
    Write errors:           0
    Total transferred:      186000 bytes
    HTML transferred:       26000 bytes
    Requests per second:    1451.72 [#/sec] (mean)
    Time per request:       34.442 [ms] (mean)
    Time per request:       0.689 [ms] (mean, across all concurrent requests)
    Transfer rate:          131.85 [Kbytes/sec] received

    Connection Times (ms)
                  min  mean[+/-sd] median   max
    Connect:        0    0   0.2      0       2
    Processing:    31   34   1.4     33      42
    Waiting:       31   33   1.4     33      42
    Total:         31   34   1.5     34      43

    Percentage of the requests served within a certain time (ms)
      50%     34
      66%     34
      75%     35
      80%     35
      90%     36
      95%     36
      98%     38
      99%     40
     100%     43 (longest request)

### Results

Placing operations that return Responses from those operation that do intense computation or IO
into separate execution contexts (thread pools) of Play doubles the throughput.

## More reading:

1. http://engineering.linkedin.com/play/play-framework-async-io-without-thread-pool-and-callback-hell
2. http://www.playframework.com/documentation/2.1.1/ScalaAsync
3. http://www.playframework.com/documentation/2.1.1/JavaAsync
4. http://www.playframework.com/documentation/2.1.0/ThreadPools
5. http://doc.akka.io/docs/akka/snapshot/scala/dispatchers.html
