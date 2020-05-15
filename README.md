# zuora-full-export

This app automates full export of large Zuora objects by splitting the export into monthly chunks and provides
resume capabilities in case of failures or interruptions.

Zuora terminates export job after 8 hours. This means full export of large objects is not possible in one go
if they take more than 8 hours. Zuora advised to switch to Stateful API which brings over increments of changed 
records, however the problem remains even with Stateful API for the initial export which has to be a full export.
**Be warned, despite Zuora documenting Stateful API as capable of full export, this is not true for large objects.**

## User Guide

The app is idempotent and it can resume from where it left of before failure/termination. 
It means you can re-run it as many time as possible. It can be terminated at any point by say
Control + C or network failure and then resumed from the previous completed month. 

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

Run it with `sbt run`. The logs provide realtime feedback

```
2020-05-15 17:00:57,193 INFO Exporting zuora objects:
Iterable
  ZuoraObject
    name = RatePlanCharge
    fields: Iterable
      Id
      CreatedDate
      UpdatedDate
  ZuoraObject
    name = InvoiceItem
    fields: Iterable
      Id
      CreatedDate
      UpdatedDate

2020-05-15 17:00:57,272 INFO Resume RatePlanCharge from 2020-01-01
2020-05-15 17:00:57,272 INFO Resume InvoiceItem from 2020-01-01
2020-05-15 17:00:59,026 INFO Exporting InvoiceItem 2020-01-01 to 2020-02-01 chunk (1/5) by job 2c92c0f872125af20172191139a00b41
2020-05-15 17:00:59,181 INFO Exporting RatePlanCharge 2020-01-01 to 2020-02-01 chunk (1/5) by job 2c92c0f872125aeb017219113ac71c8d
2020-05-15 17:00:59,329 INFO Checking if getJobResult(2c92c0f872125af20172191139a00b41) is done...
2020-05-15 17:00:59,443 INFO Checking if getJobResult(2c92c0f872125aeb017219113ac71c8d) is done...
2020-05-15 17:01:04,558 INFO Checking if getJobResult(2c92c0f872125af20172191139a00b41) is done...
2020-05-15 17:01:04,692 INFO Downloading Batch(2c92c0f872125aeb017219113acd1c8e,RatePlanCharge-2020-01-01,completed,7193,Some(2c92c08572125ab20172191147d44a32)) ....
2020-05-15 17:01:05,427 INFO Completed converting downloaded RatePlanCharge content to lines
2020-05-15 17:01:05,433 INFO Completed RatePlanCharge header processing
2020-05-15 17:01:05,433 INFO Appending RatePlanCharge lines to .csv file
2020-05-15 17:01:05,460 INFO Done RatePlanCharge 2020-01-01 to 2020-02-01 chunk (1/5) with record count 7193 exported by job 2c92c0f872125aeb017219113ac71c8d
2020-05-15 17:01:05,987 INFO Exporting RatePlanCharge 2020-02-01 to 2020-03-01 chunk (2/5) by job 2c92c0f872125af201721911553f0cab
2020-05-15 17:01:06,213 INFO Checking if getJobResult(2c92c0f872125af201721911553f0cab) is done...
2020-05-15 17:01:09,876 INFO Downloading Batch(2c92c0f872125af20172191139a50b42,InvoiceItem-2020-01-01,completed,204735,Some(2c92c086721266200172191157f6394d)) ....
2020-05-15 17:01:11,546 INFO Downloading Batch(2c92c0f872125af20172191155440cac,RatePlanCharge-2020-02-01,completed,9149,Some(2c92c08572125ab2017219115ee14a43)) ....
...
2020-05-15 17:01:42,809 INFO All InvoiceItem chunks exported!
2020-05-15 17:01:42,810 INFO Successfully completed full export of List(RatePlanCharge, InvoiceItem)
```

## Auto-discovery of field names

Running the app with `sbt "run auto"` will query Zuora to auto-detect all object field names as well as IDs of 
related objects and export them in alphabetical order. Makre sure to adjust your ingestion schemas accordingly.






