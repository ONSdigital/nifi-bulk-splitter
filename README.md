
## Description ##

Two custom NiFi processors:

1. Split a JSON array into small chunks based on a configurable batch size.
2. Delete a cache entry from Redis. Should work with an NiFi cache implementation.

This forms part of the NiFi bulk processing flow for the ONS Address Index API.

## Details ##

Uses NiFi version 1.7.1 (note that 1.8.0 has a bug re. connecting to Redis). 

