<img height="80px" src="/resources/roll.png">

# Roll framework

>  roll /ˈrōl/
>
>  _informal_. to begin to move or operate; start; commence.
>  _Let's roll at sunrise._

<br>

__Roll__ makes it easy for your project to include a ___Webserver___ ([Http-kit](http://www.http-kit.org/) or [Aleph](https://aleph.io/)), ___Websockets___ ([Sente](https://github.com/ptaoussanis/sente)), ___REPL___ ([nREPL](https://github.com/clojure-emacs/cider-nrepl)), ___Routing___ ([Reitit](https://github.com/metosin/reitit)), ___Task Scheduling___ ([Chime](https://github.com/jarohen/chime)), ___File Watching___ ([Hawk](https://github.com/wkf/hawk)) and ___Logging___ ([Timbre](https://github.com/ptaoussanis/timbre)). Configure and manage them using a simple config file ([Integrant](https://github.com/weavejester/integrant)).

<br>

## Leiningen

`roll` is published on [Clojars](https://clojars.org/dimovich/roll).
Add the following to your `project.clj`'s `:dependencies`:

    [dimovich/roll "0.3.2"]


## Clojure CLI/deps.edn

	dimovich/roll {:mvn/version "0.3.2"}


## Example

(To run this you'll need to install [Clojure CLI tools](https://clojure.org/guides/getting_started).)
<br><br>

__deps.edn__

``` clojure
{:paths ["src"]

 :deps {dimovich/roll {:mvn/version "0.3.2"}}}
```



__config.edn__

```clojure
{:roll/httpkit {:port 5000}

 :roll/handler {:routes [["/" example.server/index]]}}
```



__src/example/server.clj__

``` clojure
(ns example.server
  (:require [roll.core]))


(defn index [req]
  {:status 200 :body "Hello World!"})


(defn -main []
  (roll.core/init "config.edn"))
```



### Start

```
clj -m example.server
```

Navigate to [localhost:5000](http://localhost:5000).

<br>

## Example Projects

[Basic](/example), [Descryptors](https://github.com/descryptors/descryptors), [Destreams](https://github.com/descryptors/destreams).

<br>

For all possible options see [config.edn](/config.edn).

For Aleph support see [this git branch](https://github.com/dimovich/roll/tree/aleph).
