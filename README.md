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

## Upload large CSV files to raw datalake buckets

This step is currently not automated. Import fresh janus credentials and execute the following AWS CLI commands. Note 
upload commands have to include `--acl`: 

```scala
aws s3 cp  Account.csv              s3://ophan-raw-zuora-increment-account/            --profile membership
aws s3 cp  RatePlanCharge.csv       s3://ophan-raw-zuora-increment-rateplancharge/     --profile membership
aws s3 cp  RatePlanChargeTier.csv   s3://ophan-raw-zuora-increment-rateplanchargetier/ --profile membership
aws s3 cp  RatePlan.csv             s3://ophan-raw-zuora-increment-rateplan/           --profile membership
aws s3 cp  Subscription.csv         s3://ophan-raw-zuora-increment-subscription/       --profile membership
aws s3 cp  Contact.csv              s3://ophan-raw-zuora-increment-contact/            --profile membership
aws s3 cp  PaymentMethod.csv        s3://ophan-raw-zuora-increment-paymentmethod/      --profile membership
aws s3 cp  Amendment.csv            s3://ophan-raw-zuora-increment-amendment/          --profile membership
aws s3 cp  Invoice.csv              s3://ophan-raw-zuora-increment-invoice/            --profile membership
aws s3 cp  Payment.csv              s3://ophan-raw-zuora-increment-payment/            --profile membership
aws s3 cp  InvoicePayment.csv       s3://ophan-raw-zuora-increment-invoicepayment/     --profile membership
aws s3 cp  Refund.csv               s3://ophan-raw-zuora-increment-refund/             --profile membership
aws s3 cp  InvoiceItem.csv          s3://ophan-raw-zuora-increment-invoiceitem/        --profile membership
```

## Auto-discovery of field names

Running the app with `sbt "run auto"` will query Zuora to auto-detect all object field names as well as IDs of 
related objects and export them in alphabetical order. Make sure to adjust your ingestion schemas accordingly.

## (Obsolete) How to perform manual full export? 

_This is just for informational purposes and documents the time-consuming and error-prone steps we had to take
before `zuora-full-export` app was operational._

1. Zuora is not capable of doing a full export of InvoiceItem hence the WHERE clause: https://support.zuora.com/hc/en-us/requests/186104

    ```
     WHERE (CreatedDate >= '2019-08-28T00:00:00') AND (CreatedDate <= '2099-01-01T00:00:00')
    ```
1. Manually, using postman perform year by year exports:

    ```
    WHERE (CreatedDate >= '2014-01-01T00:00:00') AND (CreatedDate <= '2015-01-01T00:00:00')
    WHERE (CreatedDate >= '2015-01-01T00:00:00') AND (CreatedDate <= '2016-01-01T00:00:00')
    ...
    ```
1. Download locally CSVs from https://www.zuora.com/apps/BatchQuery.do
1. Concatenate all the CSV
    
    ```
    cat InvoiceItem-1.csv > InvoiceItem.csv
    tail -n +2 InvoiceItem-2.csv >> InvoiceItem.csv
    tail -n +2 InvoiceItem-3.csv >> InvoiceItem.csv
    ...
1. Resulting `InvoiceItem.csv` should be >18GB and have >20M records
1. Perform some sanity checks, for example, `wc -l`
1. Manually upload resulting CSV to bucket `https://s3.console.aws.amazon.com/s3/buckets/ophan-raw-zuora-increment-invoiceitem` 
1. Run ophan job
1. Adjust to filter from yesterday, so if yesterday is 2020-12-20, then 
   
    ```
    WHERE (CreatedDate >= '2020-12-20T00:00:00') AND (CreatedDate <= '2099-01-01T00:00:00')
    ``` 






