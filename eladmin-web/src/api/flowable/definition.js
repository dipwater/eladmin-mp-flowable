import request from '@/utils/request'

export function getDefinitions(params) {
  return request({
    url: '/api/flowable/definition',
    method: 'get',
    params
  })
}

export function readXml(id) {
  return request({
    url: 'api/flowable/definition/xml/' + id,
    method: 'get',
    responseType: 'text' // Important to get raw text
  })
}

// Model API
export function getModels(params) {
  return request({
    url: 'api/flowable/model',
    method: 'get',
    params
  })
}

export function saveModel(data) {
  return request({
    url: 'api/flowable/model',
    method: 'post',
    data
  })
}

export function deployModel(id) {
  return request({
    url: 'api/flowable/model/deploy/' + id,
    method: 'post'
  })
}

export function delModel(id) {
  return request({
    url: 'api/flowable/model/' + id,
    method: 'delete'
  })
}

export function readModelXml(id) {
  return request({
    url: 'api/flowable/model/xml/' + id,
    method: 'get',
    responseType: 'text'
  })
}

export function deploy(data) {
  return request({
    url: '/api/flowable/definition/deploy',
    method: 'post',
    data
  })
}

export function updateState(id, state) {
  return request({
    url: '/api/flowable/definition/' + id + '?state=' + state,
    method: 'put'
  })
}

export function del(id) {
  return request({
    url: '/api/flowable/deployment/' + id,
    method: 'delete'
  })
}

export function readResource(id) {
  return request({
    url: '/api/flowable/definition/image/' + id,
    method: 'get',
    responseType: 'blob'
  })
}
