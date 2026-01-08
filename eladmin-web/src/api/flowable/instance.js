import request from '@/utils/request'

export function getInstances(params) {
  return request({
    url: '/api/flowable/instance',
    method: 'get',
    params
  })
}

export function deleteInstance(id, deleteReason) {
  return request({
    url: '/api/flowable/instance/' + id,
    method: 'delete',
    params: { deleteReason }
  })
}

export function updateInstanceState(id, state) {
  return request({
    url: '/api/flowable/instance/' + id + '?state=' + state,
    method: 'put'
  })
}
