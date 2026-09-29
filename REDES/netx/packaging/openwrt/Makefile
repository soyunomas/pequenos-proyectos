include $(TOPDIR)/rules.mk

PKG_NAME:=netx
PKG_VERSION:=0.5.0
PKG_RELEASE:=1

include $(INCLUDE_DIR)/package.mk

define Package/netx
  SECTION:=net
  CATEGORY:=Network
  TITLE:=netx network measurement and diagnostics
  URL:=https://github.com/soyunomas/pequenos-proyectos/tree/main/REDES/netx
endef

define Package/netx/description
 netx is a static Go network measurement tool that keeps transport goodput,
 available-bandwidth estimates and responsiveness as separate magnitudes.
endef

define Build/Compile
	@test -x ./files/netx || (echo "files/netx missing; stage this package with scripts/openwrt-feed.sh" >&2; exit 1)
endef

define Package/netx/conffiles
/etc/config/netx
endef

define Package/netx/install
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) ./files/netx $(1)/usr/bin/netx
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/netx.init $(1)/etc/init.d/netx
	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./files/netx.config $(1)/etc/config/netx
endef

$(eval $(call BuildPackage,netx))
