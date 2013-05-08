package controllers;

import play.libs.F;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;

public class WebServices extends Controller {
    private static String url = "http://www.google.com";

    public static Result good() {
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

}
