## Bittrex Trading Daemon (trexd)

### Features:

- Setup email alert for:
  - When price reaches ? rate.
  - When limit buy/sell order is completed.
- View, create and cancel orders.
- Simple web based interface.
- Fully scriptable (Uses clojure REPL)
- All services run on your machine, no hidden third party.
- And more to come..?



### Setup:

Download then extract latest release.

In cmd / shell:

cd trexd

java -jar trexd-*.jar

Follow setup steps given by the program and it should setup a working webserver in no time.

GUI is hosted locally on  localhost:8080