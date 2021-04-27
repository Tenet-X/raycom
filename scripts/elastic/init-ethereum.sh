#!/bin/bash

pwd=`pwd`

es_client=https://k8es.client.bindiego.com
kbn_host=https://k8na.bindiego.com
es_user=elastic
es_pass=$(<espass)

[ -f $pwd/espass ] || touch espass

if [ ! -s $pwd/espass ]
then
    echo "espass file is empty, please set the Elasticsearch password there"
    exit 1
fi

# Create an ES pipeline for GCLB logs
__create_index_pipeline() {
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_ingest/pipeline/gclb" \
        -H "Content-Type: application/json" \
        -d "@${pwd}/index-gclb-pipeline.json"
}

# create an ES template
__create_index_template() {
    # ethereum node rpc aquired block data
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/ethdata-blk" \
        -H "Content-Type: application/json" \
        -d "@${pwd}/index-ethdata-blk-template.json"
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/ethdata-blk"

    # ethereum node rpc aquired transaction data
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/ethdata-tx" \
        -H "Content-Type: application/json" \
        -d "@${pwd}/index-ethdata-tx-template.json"  
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/ethdata-tx"  
}

__create_index_and_setup() {
    # create a lifecycle pocily, edit the json data file according to your needs
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_ilm/policy/ethereum-policy" \
        -H "Content-Type: application/json" \
        -d "@${pwd}/index-ethereum-policy.json"

    # index: ethdata-blk*
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/ethdata-blk-000001" \
        -H "Content-Type: application/json" \
        -d '{"aliases": {"ethdata-blk-ingest": { "is_write_index": true }}}'
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/ethdata-blk*/_ilm/explain"

    # index: ethdata-tx*
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/ethdata-tx-000001" \
        -H "Content-Type: application/json" \
        -d '{"aliases": {"ethdata-tx-ingest": { "is_write_index": true }}}'
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/ethdata-tx*/_ilm/explain"
}

# create a Kibana index pattern
__create_index_pattern() {
    # index: ethdata-blk*
    curl -X POST \
        -u "${es_user}:${es_pass}" \
        "${kbn_host}/api/saved_objects/index-pattern" \
        -H "kbn-xsrf: true" \
        -H "Content-Type: application/json" \
        -d '{"attributes":{"title":"ethdata-blk*","timeFieldName":"@timestamp","fields":"[]"}}'

    # index: ethdata-tx*
    curl -X POST \
        -u "${es_user}:${es_pass}" \
        "${kbn_host}/api/saved_objects/index-pattern" \
        -H "kbn-xsrf: true" \
        -H "Content-Type: application/json" \
        -d '{"attributes":{"title":"ethdata-tx*","timeFieldName":"block_timestamp","fields":"[]"}}'
}

#__create_index_pipeline
__create_index_template
__create_index_and_setup
__create_index_pattern
