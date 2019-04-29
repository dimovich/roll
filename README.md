<img height="80px" src="/resources/roll.png">

# Roll metaframework

>  roll   /ˈrōl/
>
>  _informal_. to begin to move or operate; start; commence.
>  _Let's roll at sunrise._

<br>

__Roll__ makes it easy for your project to include a ___Webserver___ ([Nginx](https://github.com/nginx-clojure/nginx-clojure), [Http-kit](http://www.http-kit.org/) or [Aleph](https://github.com/ztellman/aleph)), ___Websockets___ ([Sente](https://github.com/ptaoussanis/sente)), ___REPL___ ([nREPL](https://github.com/clojure-emacs/cider-nrepl)), ___Routing___ ([Reitit](https://github.com/metosin/reitit)) and ___File Watching___. Configure and manage them using a simple config file ([Integrant](https://github.com/weavejester/integrant)).

<br><br>

## Example
__config.edn__

```clojure
{:roll/httpkit {:port 5000}

 :roll/paths ["config.edn" {:watch roll.core/init}] ;; reload system when config.edn changes

 :roll/handler {:routes [["/" example.core/handler]]}}
```


__deps.edn__

``` clojure
{:paths ["src"]

 :deps {roll {:git/url "https://github.com/dimovich/roll"
              :sha "9b4c498e949c5612367d321da1dc6401378c9952"}}}
```


__src/example/core.clj__

``` clojure
(ns example.core
  (:require [roll.core :as roll]))

(defn handler [req]
  {:status 200 :body "Hello World!"})

(defn -main []
  (roll/init "config.edn"))
```


### Start

```
clj -m example.core
```
