# Cassandra Files API.

A simple ReST service built on [Containium](https://github.com/containium/containium) for retrieving files from the [Cassandra File Repository](https://github.com/vizanto/valueobjects/tree/master/filerepository/cassandra).


## Usage

### Configuration and deployment.

Deploy just as any other Containium Ring application.
Example deployment descriptor:

```
{:file "path/to/cassandra-file-api", {:ring {:context-path "/", :host-regex "cdn.example.com"}, :swappable? true}}
```


### The REST interface

Only one action is supported: a GET request where the path is the hash of the desired file.
The result is either the file data (code 200), the indication the file has not changed (code 304), or the file is not known (code 404).

- Supports the `If-Modified-Since` header.
- CORS support is enabled allowing any domain to issue GET requests.
- A `crossdomain.xml` file is served from resources, allowing any GET request to be issued by Flash Player.


## License

Mozilla Public License 2.0
