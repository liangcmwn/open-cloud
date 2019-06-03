package com.opencloud.zuul.locator;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.opencloud.base.client.model.entity.GatewayRoute;
import com.opencloud.common.event.GatewayRemoteRefreshRouteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cloud.netflix.zuul.filters.SimpleRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties.ZuulRoute;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义动态路由加载器
 *
 * @author: liuyadu
 * @date: 2018/10/23 10:31
 * @description:
 */
@Slf4j
public class JdbcRouteLocator extends SimpleRouteLocator implements ApplicationListener<GatewayRemoteRefreshRouteEvent> {

    private JdbcTemplate jdbcTemplate;
    private ZuulProperties properties;
    private List<GatewayRoute> routeList;

    public JdbcRouteLocator(String servletPath, ZuulProperties properties, JdbcTemplate jdbcTemplate) {
        super(servletPath, properties);
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 加载数据库路由配置
     *
     * @return
     */
    @Override
    protected Map<String, ZuulProperties.ZuulRoute> locateRoutes() {
        LinkedHashMap<String, ZuulProperties.ZuulRoute> routesMap = Maps.newLinkedHashMap();
        routesMap.putAll(super.locateRoutes());
        //从db中加载路由信息
        routesMap.putAll(loadRoutes());
        //优化一下配置
        LinkedHashMap<String, ZuulProperties.ZuulRoute> values = Maps.newLinkedHashMap();
        for (Map.Entry<String, ZuulProperties.ZuulRoute> entry : routesMap.entrySet()) {
            String path = entry.getKey();
            // Prepend with slash if not already present.
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (StringUtils.hasText(this.properties.getPrefix())) {
                path = this.properties.getPrefix() + path;
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
            }
            values.put(path, entry.getValue());
        }
        return values;
    }

    @Override
    public void doRefresh() {
        super.doRefresh();
    }

    /**
     * @return
     * @description 加载路由配置，由子类去实现
     * @date 2017年7月3日 下午6:04:42
     * @version 1.0.0
     */
    public Map<String, ZuulRoute> loadRoutes() {
        Map<String, ZuulProperties.ZuulRoute> routes = Maps.newLinkedHashMap();
        routeList = Lists.newArrayList();
        try {
            routeList = jdbcTemplate.query("SELECT * FROM gateway_route WHERE status = 1", new RowMapper<GatewayRoute>() {
                @Override
                public GatewayRoute mapRow(ResultSet rs, int i) throws SQLException {
                    GatewayRoute route = new GatewayRoute();
                    route.setRouteId(rs.getLong("route_id"));
                    route.setPath(rs.getString("path"));
                    route.setServiceId(rs.getString("service_id"));
                    route.setUrl(rs.getString("url"));
                    route.setStatus(rs.getInt("status"));
                    route.setRetryable(rs.getInt("retryable"));
                    route.setStripPrefix(rs.getInt("strip_prefix"));
                    route.setIsPersist(rs.getInt("is_persist"));
                    route.setRouteName(rs.getString("route_name"));
                    return route;
                }
            });
            if (routeList != null && routeList.size() > 0) {
                for (GatewayRoute result : routeList) {
                    if (StringUtils.isEmpty(result.getPath())) {
                        continue;
                    }
                    if (StringUtils.isEmpty(result.getServiceId()) && StringUtils.isEmpty(result.getUrl())) {
                        continue;
                    }
                    ZuulProperties.ZuulRoute zuulRoute = new ZuulProperties.ZuulRoute();

                    BeanUtils.copyProperties(result, zuulRoute);
                    zuulRoute.setId(result.getRouteName());
                    routes.put(zuulRoute.getPath(), zuulRoute);
                }
            }
            log.info("=============加载动态路由:{}==============", routeList.size());
        } catch (Exception e) {
            log.error("加载动态路由错误:{}", e.getMessage());
        }
        return routes;
    }

    public List<GatewayRoute> getRouteList() {
        return routeList;
    }

    public void setRouteList(List<GatewayRoute> routeList) {
        this.routeList = routeList;
    }

    @Override
    public void onApplicationEvent(GatewayRemoteRefreshRouteEvent gatewayRemoteRefreshRouteEvent) {
        doRefresh();
    }
}
