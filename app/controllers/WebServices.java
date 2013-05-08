package controllers;

import play.libs.Akka;
import play.libs.F;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.Random;
import java.util.concurrent.Callable;

public class WebServices extends Controller {
    private static String url = "http://www.google.com";

    public static Result good() {
        // The promise actors that handle this use
        // the default execution context:  play.core.Invoker.executionContext
        final F.Promise<WS.Response> responseHolder = WS.url(url).get();
        return async(
                responseHolder.map(
                        new F.Function<WS.Response,Result>() {
                            public Result apply(WS.Response r) {
                                return ok("URL gives back " + r.getStatus());
                            }
                        })
        );
    }


    public static Result bad() {
        final F.Promise<WS.Response> responseHolder = WS.url(url).get();
        final WS.Response response = responseHolder.get();
        return ok("URL gives back " + response.getStatus());
    }

    public static Result javaBad() {
        // This is the part that needs to be run asynchronously
        // According the to Play documentation:

        /*
        When using the Java promise API, for most operations you don’t get a choice as to which execution context
        will be used, the Play default
        execution context will always be used.
         */

        // Therefore the Thread pool used to pick up finished Promise[Result]
        // to to return requests to the client is the SAME Thread Pool running the
        // sleep, which is bad
        final F.Promise<Integer> promiseOfInteger = Akka.future(
                new Callable<Integer>() {
                    public Integer call() {
                        int value;
                        try {
                            Thread.sleep(30);
                            value = (new Random(10).nextInt(10));
                        } catch (InterruptedException e) {
                             value = -1;
                        }
                        return value;
                    }
                }
        );
        return async(
                promiseOfInteger.map(
                        new F.Function<Integer, Result>() {
                            public Result apply(Integer i) {
                                return ok("Intense Computation gives back: " + i.intValue());
                            }
                        }
                )
        );
    }

}
