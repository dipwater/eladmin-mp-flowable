<template>
  <div class="app-container">
    <el-form ref="form" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="流程定义" prop="processDefinitionKey">
        <el-select
          v-model="form.processDefinitionKey"
          placeholder="请选择要启动的流程"
          style="width: 100%"
          @change="handleProcessChange"
        >
          <el-option
            v-for="item in definitions"
            :key="item.key"
            :label="item.name + ' (v' + item.version + ')'"
            :value="item.key"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入名称（例如：张三的报销申请）" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" placeholder="请输入任务描述信息" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="onSubmit">发起流程</el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
import { startProcess } from '@/api/flowable'
import { getDefinitions } from '@/api/flowable/definition'

export default {
  name: 'FlowableProcess',
  data() {
    return {
      loading: false,
      definitions: [],
      form: {
        processDefinitionKey: '',
        name: '',
        description: ''
      },
      rules: {
        processDefinitionKey: [
          { required: true, message: '请选择流程定义', trigger: 'change' }
        ],
        name: [
          { required: true, message: '请输入任务名称', trigger: 'blur' }
        ]
      }
    }
  },
  created() {
    this.loadDefinitions()
  },
  methods: {
    loadDefinitions() {
      getDefinitions({ page: 0, size: 1000 }).then(res => {
        this.definitions = res
      })
    },
    handleProcessChange(key) {
      // Auto-fill name if empty
      const def = this.definitions.find(d => d.key === key)
      if (def && !this.form.name) {
        this.form.name = def.name + '-' + new Date().toLocaleString()
      }
    },
    onSubmit() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          this.loading = true
          // Pack description into variables
          const variables = {
            description: this.form.description
          }

          startProcess(this.form.processDefinitionKey, this.form.name, variables).then(res => {
            this.$notify({
              title: '成功',
              message: '流程启动成功，实例ID: ' + res,
              type: 'success',
              duration: 2000
            })
            this.loading = false
            this.form.name = ''
            this.form.description = ''
          }).catch(() => {
            this.loading = false
          })
        }
      })
    },
    resetForm() {
      this.$refs['form'].resetFields()
    }
  }
}
</script>

<style scoped>

</style>
