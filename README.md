
## Description ##

A custom NiFi  processor to split a JSON array into small chunks based on a configurable batch size.

This forms part of the NiFi bulk processing flow for the ONS Address Index API.

## Details ##

Uses NiFi version 1.7.1 (note that 1.8.0 has a bug re. connecting to Redis). 

Extends the standard NiFi `AbstractJsonPathProcessor` class and therefore needs to be bundled with `nifi-standard-processors` and `nifi-standard-services-api-nar`.
