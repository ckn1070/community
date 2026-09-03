@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "account :: api",
      "media :: api",
      "notification :: api",
      "shared :: error",
      "shared :: integration"
    })
package com.ronams.carmunity.community;
