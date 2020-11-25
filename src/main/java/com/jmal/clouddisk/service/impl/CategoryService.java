package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.Category;
import com.jmal.clouddisk.model.CategoryDTO;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:51 下午
 */
@Service
public class CategoryService {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String USERID_PARAM = "userId";

    private static final String COLLECTION_NAME = "category";

    /***
     * 分类列表
     * @param userId 用户id
     * @return 一级分类列表
     */
    public List<CategoryDTO> list(String userId, String parentId) {
        // Query query = new Query();
        // query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        // if (StringUtils.isEmpty(parentId)) {
        //     query.addCriteria(Criteria.where("parentCategoryId").in("", null));
        // } else {
        //     query.addCriteria(Criteria.where("parentCategoryId").is(parentId));
        // }
        // List<Category> categoryList = mongoTemplate.find(query, Category.class, COLLECTION_NAME);
        // return categoryList.stream().map(category -> {
        //     CategoryDTO categoryDTO = new CategoryDTO();
        //     CglibUtil.copy(category, categoryDTO);
        //     String parentCategoryId = category.getId();
        //     List<Category> subCategoryList = getSubCategoryList(userId, parentCategoryId);
        //     categoryDTO.setSubCategorySize(subCategoryList.size());
        //     getCategoryArticlesNum(categoryDTO);
        //     return categoryDTO;
        // }).sorted().collect(Collectors.toList());
        List<CategoryDTO> categoryTreeList = tree(userId, true);
        categoryTreeList = getSubCategoryList(parentId, categoryTreeList);
        return categoryTreeList.stream().peek(categoryDTO -> {
            categoryDTO.setSubCategorySize(categoryDTO.getChildren().size());
            categoryDTO.setChildren(null);
        }).collect(Collectors.toList());
    }

    /***
     * 根据分类树查询某个分类的子分类
     * @param parentCategoryId 父分类id
     * @param categoryTreeList 分类数
     * @return 分类列表
     */
    private List<CategoryDTO> getSubCategoryList(String parentCategoryId, List<CategoryDTO> categoryTreeList) {
        List<CategoryDTO> categoryDTOList = new ArrayList<>();
        if(StringUtils.isEmpty(parentCategoryId)){
            return categoryTreeList;
        }
        for (CategoryDTO categoryDTO : categoryTreeList) {
            if(categoryDTO.getChildren() == null){
                continue;
            }
            if(parentCategoryId.equals(categoryDTO.getId())){
                return categoryDTO.getChildren();
            }
            return getSubCategoryList(parentCategoryId, categoryDTO.getChildren());
        }
        return categoryDTOList;
    }

    /***
     * 获取分类的文章数
     * @param categoryDTO categoryDTO
     */
    private void getCategoryArticlesNum(CategoryDTO categoryDTO) {
        Query query = new Query();
        query.addCriteria(Criteria.where("categoryIds").is(categoryDTO.getId()));
        long count = mongoTemplate.count(query, FileServiceImpl.COLLECTION_NAME);
        categoryDTO.setArticleNum(Convert.toInt(count));
    }

    /***
     * 分类树
     * @param userId 用户id
     * @return 分类树结构
     */
    public List<CategoryDTO> tree(String userId, boolean statArticleNum) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        List<Category> categoryList = mongoTemplate.find(query, Category.class, COLLECTION_NAME);
        List<CategoryDTO> categoryDTOList = categoryList.parallelStream().map(category -> {
            CategoryDTO categoryDTO = new CategoryDTO();
            CglibUtil.copy(category, categoryDTO);
            if (statArticleNum) {
                getCategoryArticlesNum(categoryDTO);
            }
            return categoryDTO;
        }).collect(Collectors.toList());
        Map<String, Integer> articleNumMap = null;
        if (statArticleNum) {
            articleNumMap = new HashMap<>(16);
        }
        List<CategoryDTO> categoryTreeList = getSubCategory(null, categoryDTOList, articleNumMap);
        return getArticleNum(articleNumMap, categoryTreeList);
    }

    /***
     * 查找分类的子集
     * @param parentCategoryId 父分类id
     * @param categoryDTOList 分类列表
     * @return 分类列表
     */
    private List<CategoryDTO> getSubCategory(String parentCategoryId, List<CategoryDTO> categoryDTOList, Map<String, Integer> articleNumMap) {
        List<CategoryDTO> categoryTreeList = new ArrayList<>();
        List<CategoryDTO> categoryList;
        if (StringUtils.isEmpty(parentCategoryId)) {
            categoryList = categoryDTOList.stream().filter(category -> StringUtils.isEmpty(category.getParentCategoryId())).sorted().collect(Collectors.toList());
        } else {
            categoryList = categoryDTOList.stream().filter(category -> parentCategoryId.equals(category.getParentCategoryId())).collect(Collectors.toList());
        }
        categoryList.forEach(subCategory -> {
            String parentId = subCategory.getParentCategoryId();
            if (articleNumMap != null && parentId != null) {
                // 统计文章数
                int num = subCategory.getArticleNum();
                if (articleNumMap.containsKey(parentId)) {
                    num += articleNumMap.get(parentId);
                } else {
                    articleNumMap.put(parentId, num);
                }
                articleNumMap.put(parentId, num);
            }
            List<CategoryDTO> subList = getSubCategory(subCategory.getId(), categoryDTOList, articleNumMap);
            if (!subList.isEmpty()) {
                subCategory.setChildren(subList);
            }
            categoryTreeList.add(subCategory);
        });
        return categoryTreeList;
    }

    /***
     * 获取文章数
     * @param articleNumMap 每个分类的文章数
     * @param categoryTreeList 分类树
     * @return 分类树
     */
    private List<CategoryDTO> getArticleNum(Map<String,Integer> articleNumMap, List<CategoryDTO> categoryTreeList) {
        if(articleNumMap == null || categoryTreeList == null){
            return categoryTreeList;
        }
        categoryTreeList = categoryTreeList.stream().peek(category -> {
            if(articleNumMap.containsKey(category.getId())){
                category.setArticleNum(articleNumMap.get(category.getId()));
            }
            List<CategoryDTO> subList = category.getChildren();
            if(subList != null && !subList.isEmpty()){
                category.setChildren(getArticleNum(articleNumMap, subList));
            }
        }).collect(Collectors.toList());
        return categoryTreeList;
    }

    /***
     * 更具id查询分类列表
     * @param categoryIds 分类id集合
     * @return 分类列表
     */
    public List<Category> getCategoryListByIds(String[] categoryIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(categoryIds));
        return mongoTemplate.find(query, Category.class, COLLECTION_NAME);
    }

    /***
     * 获取分类信息
     * @param userId 用户id
     * @param categoryName 分类名称
     * @return 一个分类信息
     */
    public Category getCategoryInfo(String userId, String categoryName) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        query.addCriteria(Criteria.where("name").is(categoryName));
        return mongoTemplate.findOne(query, Category.class, COLLECTION_NAME);
    }

    /***
     * 通过Id获取分类信息
     * @param categoryId 分类id
     * @return 一个分类信息
     */
    public Category getCategoryInfo(String categoryId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(categoryId));
        return mongoTemplate.findOne(query, Category.class, COLLECTION_NAME);
    }

    /***
     * 添加分类
     * @param categoryDTO 参数对象
     * @return ResponseResult
     */
    public ResponseResult<Object> add(CategoryDTO categoryDTO) {
        Category category1 = getCategoryInfo(categoryDTO.getUserId(), categoryDTO.getName());
        if (category1 != null) {
            return ResultUtil.warning("该分类名称已存在");
        }
        if (!StringUtils.isEmpty(categoryDTO.getParentCategoryId())) {
            Category parentCategory = getCategoryInfo(categoryDTO.getParentCategoryId());
            if (parentCategory == null) {
                return ResultUtil.warning("该父分类不存在");
            }
        }
        if (StringUtils.isEmpty(categoryDTO.getThumbnailName())) {
            categoryDTO.setThumbnailName(categoryDTO.getName());
        }
        Category category = new Category();
        CglibUtil.copy(categoryDTO, category);
        mongoTemplate.save(category);
        return ResultUtil.success();
    }

    /***
     * 更新分类
     * @param categoryDTO 参数对象
     * @return ResponseResult
     */
    public ResponseResult<Object> update(CategoryDTO categoryDTO) {
        Category category1 = getCategoryInfo(categoryDTO.getId());
        if (category1 == null) {
            return ResultUtil.warning("该分类不存在");
        }
        if (StringUtils.isEmpty(categoryDTO.getThumbnailName())) {
            categoryDTO.setThumbnailName(categoryDTO.getName());
        }
        Category category = new Category();
        CglibUtil.copy(categoryDTO, category);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(categoryDTO.getId()));
        Update update = MongoUtil.getUpdate(category);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 设置默认分类
     * @param categoryId categoryId
     * @return ResponseResult
     */
    public ResponseResult<Object> setDefault(String categoryId) {
        Query query2 = new Query();
        query2.addCriteria(Criteria.where("isDefault").is(true));
        Update update2 = new Update();
        update2.set("isDefault", false);
        mongoTemplate.updateMulti(query2, update2, COLLECTION_NAME);
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").is(categoryId));
        Update update1 = new Update();
        update1.set("isDefault", true);
        mongoTemplate.upsert(query1, update1, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 删除分类及其子分类下的所有分类
     * @param categoryIdList 分类id列表
     */
    public void delete(List<String> categoryIdList) {
        List<String> categoryIds = findLoopCategory(true, categoryIdList);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(categoryIds));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }

    /***
     * 递归查找分类id及其子分类id列表
     * @param firstCategory 是否是第一次查找
     * @param categoryIdList 分类id列表
     */
    private List<String> findLoopCategory(boolean firstCategory, List<String> categoryIdList) {
        final List<String> categoryIds = new ArrayList<>();
        Query query = new Query();
        if (firstCategory) {
            query.addCriteria(Criteria.where("_id").in(categoryIdList));
        } else {
            query.addCriteria(Criteria.where("parentCategoryId").in(categoryIdList));
            categoryIds.addAll(categoryIdList);
        }
        List<String> categoryIdList1 = mongoTemplate.find(query, Category.class, COLLECTION_NAME).stream().map(Category::getId).collect(Collectors.toList());
        if (categoryIdList1.size() > 0) {
            categoryIds.addAll(findLoopCategory(false, categoryIdList1));
        }
        return categoryIds;
    }

}
