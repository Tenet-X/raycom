pwd := $(shell pwd)
ipaddr := $(shell hostname -I | cut -d ' ' -f 1)
region := asia-east1
workerType := e2-standard-2
workerZone := b
project := devopev
job := chain-data-dev
gcsRoot := devopev-blockchain
numWorkers := 2
maxWorkers := 20
eshost := https://k8es.ingest.bindiego.com
esuser := polkadot-data-ingest
espass=`cat espass`
esindex := polkadot-ingest
esBatchSize := 2000
esBatchBytes := 10485760
esNumThread := 2
blkIdx := polkadata-blk-ingest
txIdx := polkadata-tx-ingest
bqblk := gs://$(gcsRoot)/bqschema/polkadata-blk.json
bqtx := gs://$(gcsRoot)/bqschema/polkadata-tx.json
bqblktbl := $(project):polkadot.block
bqtxtbl := $(project):polkadot.transaction
logssub := polkadot-logs-sub
datasub := polkadot-data-sub
ethblkIdx := ethdata-blk-ingest
ethtxIdx := ethdata-tx-ingest
ethbqblk := gs://$(gcsRoot)/bqschema/ethdata-blk.json
ethbqtx := gs://$(gcsRoot)/bqschema/ethdata-tx.json
ethbqblktbl := $(project):eth.block
ethbqtxtbl := $(project):eth.transaction
ethBlkSub := ethbeat.blocks-sub
ethTxSub := ethbeat.transactions-sub

init:
	@[ -f espass ] || touch espass

bqschema: gsc
	@gsutil -m cp schemas/polkadata-blk.json $(bqblk)
	@gsutil -m cp schemas/polkadata-tx.json $(bqtx)
	@gsutil -m cp schemas/ethdata-blk.json $(ethbqblk)
	@gsutil -m cp schemas/ethdata-tx.json $(ethbqtx)

gcs:
        @-gsutil mb -p $(project) \
                -c STANDARD \
                -l $(region) \
                -b on \
                gs://$(gcsRoot)

dfup: init bqschema
	@mvn -Pdataflow-runner compile exec:java \
        -Dexec.mainClass=bindiego.BindiegoStreaming \
        -Dexec.cleanupDaemonThreads=false \
        -Dexec.args="--project=$(project) \
        --streaming=true \
        --autoscalingAlgorithm=THROUGHPUT_BASED \
        --maxNumWorkers=$(maxWorkers) \
        --workerMachineType=$(workerType) \
        --diskSizeGb=32 \
        --numWorkers=$(numWorkers) \
        --tempLocation=gs://$(gcsRoot)/tmp/ \
        --gcpTempLocation=gs://$(gcsRoot)/tmp/gcp/ \
        --gcsTempLocation=gs://$(gcsRoot)/tmp/gcs/ \
        --stagingLocation=gs://$(gcsRoot)/staging/ \
        --runner=DataflowRunner \
        --topic=projects/$(project)/topics/polkadot-eco-logs \
        --subscription=projects/$(project)/subscriptions/$(logssub) \
        --polkadatasub=projects/$(project)/subscriptions/$(datasub) \
        --ethdataBlkSub=projects/$(project)/subscriptions/$(ethBlkSub) \
        --ethdataTxSub=projects/$(project)/subscriptions/$(ethTxSub) \
        --numShards=1 \
        --windowSize=6s \
        --allowedLateness=8s \
        --earlyFiringPeriod=2s \
        --lateFiringCount=1 \
        --filenamePrefix=polkadot. \
        --outputDir=gs://$(gcsRoot)/polkadot/out/ \
        --errOutputDir=gs://$(gcsRoot)/polkadot/out/err/ \
        --esHost=$(eshost) \
        --esUser=$(esuser) \
        --esPass=$(espass) \
        --esIndex=$(esindex) \
        --esMaxBatchSize=$(esBatchSize) \
        --esMaxBatchBytes=$(esBatchBytes) \
        --esNumThread=$(esNumThread) \
        --blkIdx=$(blkIdx) \
        --txIdx=$(txIdx) \
        --bqBlk=$(bqblk) \
        --bqTx=$(bqtx) \
        --bqBlkTbl=$(bqblktbl) \
        --bqTxTbl=$(bqtxtbl) \
        --ethBlkIdx=$(ethblkIdx) \
        --ethTxIdx=$(ethtxIdx) \
        --ethBqBlk=$(ethbqblk) \
        --ethBqTx=$(ethbqtx) \
        --ethBqBlkTbl=$(ethbqblktbl) \
        --ethBqTxTbl=$(ethbqtxtbl) \
        --defaultWorkerLogLevel=INFO \
        --jobName=$(job) \
        --update \
        --region=$(region) \
        --workerZone=$(region)-$(workerZone)"

df: init bqschema
	@mvn -Pdataflow-runner compile exec:java \
        -Dexec.mainClass=bindiego.BindiegoStreaming \
        -Dexec.cleanupDaemonThreads=false \
        -Dexec.args="--project=$(project) \
        --streaming=true \
        --autoscalingAlgorithm=THROUGHPUT_BASED \
        --maxNumWorkers=$(maxWorkers) \
        --workerMachineType=$(workerType) \
        --diskSizeGb=32 \
        --numWorkers=$(numWorkers) \
        --tempLocation=gs://$(gcsRoot)/tmp/ \
        --gcpTempLocation=gs://$(gcsRoot)/tmp/gcp/ \
        --gcsTempLocation=gs://$(gcsRoot)/tmp/gcs/ \
        --stagingLocation=gs://$(gcsRoot)/staging/ \
        --runner=DataflowRunner \
        --topic=projects/$(project)/topics/polkadot-eco-logs \
        --subscription=projects/$(project)/subscriptions/$(logssub) \
        --polkadatasub=projects/$(project)/subscriptions/$(datasub) \
        --ethdataBlkSub=projects/$(project)/subscriptions/$(ethBlkSub) \
        --ethdataTxSub=projects/$(project)/subscriptions/$(ethTxSub) \
        --numShards=1 \
        --windowSize=6s \
        --allowedLateness=8s \
        --earlyFiringPeriod=2s \
        --lateFiringCount=1 \
        --filenamePrefix=polkadot. \
        --outputDir=gs://$(gcsRoot)/polkadot/out/ \
        --errOutputDir=gs://$(gcsRoot)/polkadot/out/err/ \
        --esHost=$(eshost) \
        --esUser=$(esuser) \
        --esPass=$(espass) \
        --esIndex=$(esindex) \
        --esMaxBatchSize=$(esBatchSize) \
        --esMaxBatchBytes=$(esBatchBytes) \
        --esNumThread=$(esNumThread) \
        --blkIdx=$(blkIdx) \
        --txIdx=$(txIdx) \
        --bqBlk=$(bqblk) \
        --bqTx=$(bqtx) \
        --bqBlkTbl=$(bqblktbl) \
        --bqTxTbl=$(bqtxtbl) \
         --ethBlkIdx=$(ethblkIdx) \
        --ethTxIdx=$(ethtxIdx) \
        --ethBqBlk=$(ethbqblk) \
        --ethBqTx=$(ethbqtx) \
        --ethBqBlkTbl=$(ethbqblktbl) \
        --ethBqTxTbl=$(ethbqtxtbl) \
        --defaultWorkerLogLevel=INFO \
        --jobName=$(job) \
        --region=$(region) \
        --workerZone=$(region)-$(workerZone)"

cancel:
	@gcloud dataflow jobs cancel $(job) --region=$(region)

drain:
	@gcloud dataflow jobs drain $(job) --region=$(region)

.PHONY: init df dfup cancel drain baschema
