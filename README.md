<img height="80px" src="/resources/roll.png">

# Roll metaframework

>  roll   /ˈrōl/
>
>  _informal_. to begin to move or operate; start; commence.
>  _Let's roll at sunrise._

<br>

__Roll__ makes it easy for your project to include a ___Webserver___ ([Http-kit](http://www.http-kit.org/) or [Aleph](https://aleph.io/)), ___Websockets___ ([Sente](https://github.com/ptaoussanis/sente)), ___REPL___ ([nREPL](https://github.com/clojure-emacs/cider-nrepl)), ___Routing___ ([Reitit](https://github.com/metosin/reitit)) and ___File Watching___ ([Hawk](https://github.com/wkf/hawk)). Configure and manage them using a simple config file ([Integrant](https://github.com/weavejester/integrant)).

<br>

## Example

(To run this you'll need to install [Clojure CLI tools](https://clojure.org/guides/getting_started).)
<br><br>

__deps.edn__

``` clojure
{
 :paths ["src"]

 :deps {roll {:git/url "https://github.com/dimovich/roll"
              :sha "791902edd8800ac85c36016007e3290fe6bf044c"}}
}
```



__config.edn__

```clojure
{
 :roll/httpkit {:port 5000}

 :roll/handler {:routes [["/" example.core/index]]}
}
```



__src/example/core.clj__

``` clojure
(ns example.core
  (:require [roll.core]))


(defn index [req]
  {:status 200 :body "Hello World!"})


(defn -main []
  (roll.core/init "config.edn"))
```



### Start

```
clj -m example.core
```


<br>

For all possible options see [config.edn](/config.edn).

For Aleph support see [this git branch](https://github.com/dimovich/roll/tree/aleph).
