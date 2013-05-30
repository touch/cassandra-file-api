# Cassandra Files API.

This component makes a REST service available for retrieving files from the Cassandra File Repository. It contains an embedded Cassandra instance.

## Usage

### Configuration and deployment.

While not strictly necessary, this module is intended to be deployed in an Immutant server. The configuration is done through the `project.clj` file. It currently has two profiles, `:dev` and `:prod`.

When developing, one can just run `lein immutant deploy`, and the configuration from the `:dev` profile will be used. During development, an nREPL server is started on port 4343. When one wants to simulate a production deployment, one can run `lein with-profile prod immutant deploy`.

For a real production deployment, one needs to run `lein with-profile prod immutant archive`. Deploying the resulting `.ima` file will use the `:prod` configuration.

The following configuration options must be set:

- `:cassandra-config-file-path` - a String with the path to the configuration file for the embedded Cassandra instance.
- `:netty-port` - an integer for the port number where the REST service should be available.

### The REST interface

Only one action is supported: a GET request to the server (on the port as configured by `:netty-port`) where the path is the hash of the desired file. The request supports the `If-Modified-Since` header. The result is either the file data (code 200), the indication the file has not changed (code 304), or the file is not known (code 404).

## License

Mozilla Public License 2.0
