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
    # polkadot eco standard logs
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/polkadot" \
        -H "Content-Type: application/json" \
        -d "@${pwd}/index-polkadot-template.json"
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/polkadot"

    # polkadot node rpc aquired block data
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/polkadata-blk" \
        -H "Content-Type: application/json" \
        -d "@${pwd}/index-polkadata-blk-template.json"
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/polkadata-blk"

    # polkadot node rpc aquired transaction data
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/polkadata-tx" \
        -H "Content-Type: application/json" \
        -d "@${pwd}/index-polkadata-tx-template.json"  
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_template/polkadata-tx"  
}

__create_index_and_setup() {
    # create a lifecycle pocily, edit the json data file according to your needs
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/_ilm/policy/polkadot-policy" \
        -H "Content-Type: application/json" \
        -d "@${pwd}/index-polkadot-policy.json"

    # create an index and assign an alias for writing
    # index: polkadot*
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/polkadot-000001" \
        -H "Content-Type: application/json" \
        -d '{"aliases": {"polkadot-ingest": { "is_write_index": true }}}'
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/polkadot*/_ilm/explain"

    # index: polkadata-blk*
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/polkadata-blk-000001" \
        -H "Content-Type: application/json" \
        -d '{"aliases": {"polkadata-blk-ingest": { "is_write_index": true }}}'
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/polkadata-blk*/_ilm/explain"

    # index: polkadata-tx*
    curl -X PUT \
        -u "${es_user}:${es_pass}" \
        "${es_client}/polkadata-tx-000001" \
        -H "Content-Type: application/json" \
        -d '{"aliases": {"polkadata-tx-ingest": { "is_write_index": true }}}'
    # veryfy
    curl -X GET \
        -u "${es_user}:${es_pass}" \
        "${es_client}/polkadata-tx*/_ilm/explain"
}

# create a Kibana index pattern
__create_index_pattern() {
    # polkadot eco logs
    # index: polkadot*
    curl -X POST \
        -u "${es_user}:${es_pass}" \
        "${kbn_host}/api/saved_objects/index-pattern" \
        -H "kbn-xsrf: true" \
        -H "Content-Type: application/json" \
        -d '{"attributes":{"title":"polkadot*","timeFieldName":"@timestamp","fields":"[]"}}'

    # index: polkadata-blk*
    curl -X POST \
        -u "${es_user}:${es_pass}" \
        "${kbn_host}/api/saved_objects/index-pattern" \
        -H "kbn-xsrf: true" \
        -H "Content-Type: application/json" \
        -d '{"attributes":{"title":"polkadata-blk*","timeFieldName":"@timestamp","fields":"[]"}}'

    # index: polkadata-tx*
    curl -X POST \
        -u "${es_user}:${es_pass}" \
        "${kbn_host}/api/saved_objects/index-pattern" \
        -H "kbn-xsrf: true" \
        -H "Content-Type: application/json" \
        -d '{"attributes":{"title":"polkadata-tx*","timeFieldName":"@timestamp","fields":"[]"}}'
}

#__create_index_pipeline
__create_index_template
__create_index_and_setup
__create_index_pattern
