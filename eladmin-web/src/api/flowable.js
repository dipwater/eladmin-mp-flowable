import request from '@/utils/request'

export function startProcess(processDefinitionKey, name, variables) {
  return request({
    url: 'api/flowable/process/start',
    method: 'post',
    params: {
      processDefinitionKey: processDefinitionKey,
      name: name
    },
    data: variables
  })
}

export function getMyTasks(params) {
  return request({
    url: 'api/flowable/task',
    method: 'get',
    params
  })
}

export function getFinishedTasks(params) {
  return request({
    url: 'api/flowable/task/finished',
    method: 'get',
    params
  })
}

export function completeTask(data) {
  return request({
    url: 'api/flowable/task/complete',
    method: 'post',
    params: { taskId: data.taskId },
    data: data.variables
  })
}

export function readInstanceImage(id) {
  return request({
    url: 'api/flowable/instance/image/' + id,
    method: 'get',
    responseType: 'blob'
  })
}

export function getHistory(id) {
  return request({
    url: 'api/flowable/instance/history/' + id,
    method: 'get'
  })
}
export function getProcessVariables(id) {
  return request({
    url: 'api/flowable/instance/variables/' + id,
    method: 'get'
  })
}

export function rejectTask(data) {
  return request({
    url: 'api/flowable/task/reject',
    method: 'post',
    data: data
  })
}
