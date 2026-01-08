<template>
  <div class="app-container">
    <div class="head-container">
      <el-button class="filter-item" size="mini" type="primary" icon="el-icon-plus" @click="toModeler">新建流程</el-button>
      <el-button class="filter-item" size="mini" type="success" icon="el-icon-refresh" @click="toQuery">刷新</el-button>
    </div>

    <!-- Model List -->
    <el-table v-loading="loading" :data="data" size="small" style="width: 100%;">
      <el-table-column prop="key" label="流程标识" />
      <el-table-column prop="name" label="流程名称" />
      <el-table-column prop="version" label="版本" width="80" align="center" />
      <el-table-column prop="createTime" label="创建时间">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastUpdateTime" label="更新时间">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.lastUpdateTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.deploymentId" type="success">已发布</el-tag>
          <el-tag v-else type="warning">未发布</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220px" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" @click="handleDesign(scope.row)">设计</el-button>
          <el-button size="mini" type="text" @click="handleDeploy(scope.row)">发布</el-button>
          <el-popover
            :ref="'delModel' + scope.row.id"
            placement="top"
            width="180"
          >
            <p>确认删除此流程吗？</p>
            <div style="text-align: right; margin: 0">
              <el-button size="mini" type="text" @click="$refs['delModel' + scope.row.id].doClose()">取消</el-button>
              <el-button :loading="delLoading" type="primary" size="mini" @click="handleDelete(scope.row.id)">确认</el-button>
            </div>
            <el-button slot="reference" type="text" size="mini" style="color: #f56c6c; margin-left: 10px;">删除</el-button>
          </el-popover>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      :total="total"
      :current-page="page + 1"
      style="margin-top: 8px;"
      layout="total, prev, pager, next, sizes"
      @size-change="sizeChange"
      @current-change="pageChange"
    />
  </div>
</template>

<script>
// eslint-disable-next-line
import { getModels, deployModel, delModel } from '@/api/flowable/definition'
import { parseTime } from '@/utils/index'

export default {
  name: 'Definition',
  data() {
    return {
      loading: false,
      delLoading: false,
      data: [],
      page: 0,
      size: 10,
      total: 0
    }
  },
  created() {
    this.toQuery()
  },
  methods: {
    parseTime,
    toQuery() {
      this.loading = true
      getModels({ page: this.page, size: this.size }).then(res => {
        this.data = res.content || res
        this.total = res.totalElements || 0
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    pageChange(e) {
      this.page = e - 1
      this.toQuery()
    },
    sizeChange(e) {
      this.page = 0
      this.size = e
      this.toQuery()
    },
    toModeler() {
      this.$router.push('/flowable/modeler')
    },
    handleDesign(row) {
      this.$router.push({ path: '/flowable/modeler', query: { modelId: row.id }})
    },
    handleDeploy(row) {
      this.$confirm('确认发布该流程吗?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.loading = true
        deployModel(row.id).then(() => {
          this.$notify({ title: '成功', message: '发布成功', type: 'success' })
          this.toQuery()
        }).catch(() => { this.loading = false })
      })
    },
    handleDelete(id) {
      this.delLoading = true
      delModel(id).then(() => {
        this.delLoading = false
        this.$refs['delModel' + id].doClose()
        this.$notify({ title: '成功', message: '删除成功', type: 'success' })
        this.toQuery()
      }).catch(() => { this.delLoading = false })
    }
  }
}
</script>
