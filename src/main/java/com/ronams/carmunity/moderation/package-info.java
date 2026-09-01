@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "account :: api",
      "community :: api",
      "shared :: error",
      "shared :: pagination"
    })
package com.ronams.carmunity.moderation;
