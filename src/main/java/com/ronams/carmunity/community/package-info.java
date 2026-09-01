@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "account :: api",
      "media :: api",
      "notification :: api",
      "shared :: error"
    })
package com.ronams.carmunity.community;
