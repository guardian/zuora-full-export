# zuora-full-export

There are number of issues with performing full export via Zuora AQuA API
- Zuora terminates job after 8 hours so large objects such as `InvoiceItem` cannot be exported
- If job errors we cannot resume so have to start all over 
- There is no progress bar meaning we cannot predict how much longer will job take

These problems are relevant even for Stateful AQuA API because the initial export has to be full export.

This app addresses the above issues by
 - splitting the export into monthly chunks
 - provides resume capability where we can continue from previous chunk
 - provides real-time feedback on where we are in the export
 
 This app is meant to be used in conduction with Stateful AQuA API meaning after the first full export is 
 successful then switch to daily increments with https://github.com/guardian/support-service-lambdas/tree/master/handlers/zuora-datalake-export

## User Guide

**Just keep rerunning the app until the last line in the log says `Successfully completed full export...`**

The app is idempotent and it can resume from where it left of before failure/termination. 
It means you can re-run it as many time as possible. It can be terminated at any point by say
`Control+C` or network failure and then resumed from the previous completed month. 

The export is parallelised meaning each exported object gets its own thread. If exporting particular object fails
you can choose to continue running for other objects, or terminate the whole process and restart it. The export
will resume from last bookmark. 

* Complete file at `output/object.csv`
* Chunks are stored at `output/scratch/object-YYYY-MM-DD.csv`
* Logs can be tailed at `tail -f logs/zuora-full-export.log`

Provide `input.json` in the following format at the root of the project

```json
{
  "beginningOfTime": "2014-01-01",
  "objects": [
    {
      "name": "Account",
      "fields": [
        "Id",
        "CreatedDate",
        "UpdatedDate"
      ]
    },
    {
      "name": "RatePlanCharge",
      "fields": [
        "Id",
        "CreatedDate",
        "UpdatedDate"
      ]
    }
  ]
}
``` 

Provide the following environmental variables

```scala
ClientId=******
ClientSecret=******
Stage=PROD (CODE stage will target sandbox)
```

Run it with `sbt run`. The logs provide realtime feedback.

## Auto-discovery of field names

Running the app with `sbt "run auto"` will query Zuora to auto-detect all object field names as well as IDs of 
related objects and export them in alphabetical order. Makre sure to adjust your ingestion schemas accordingly.






