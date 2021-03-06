/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
#include "proton/container.hpp"
#include "proton/messaging_event.hpp"
#include "proton/connection.hpp"
#include "proton/session.hpp"
#include "proton/messaging_adapter.hpp"
#include "proton/acceptor.hpp"
#include "proton/error.hpp"
#include "proton/url.hpp"
#include "proton/sender.hpp"
#include "proton/receiver.hpp"

#include "container_impl.hpp"
#include "connector.hpp"
#include "contexts.hpp"
#include "proton/connection.h"
#include "proton/session.h"

namespace proton {

//// Public container class.

container::container(const std::string& id) :
    impl_(new container_impl(*this, 0, id)) {}

container::container(messaging_handler &mhandler, const std::string& id) :
    impl_(new container_impl(*this, &mhandler, id)) {}

container::~container() {}

connection& container::connect(const url &host, handler *h) { return impl_->connect(host, h); }

reactor &container::reactor() { return *impl_->reactor_; }

std::string container::container_id() { return impl_->container_id_; }

void container::run() { impl_->reactor_->run(); }

sender& container::create_sender(const proton::url &url) {
    return impl_->create_sender(url);
}

receiver& container::create_receiver(const proton::url &url) {
    return impl_->create_receiver(url);
}

acceptor& container::listen(const proton::url &url) {
    return impl_->listen(url);
}

void container::link_prefix(const std::string& s) { impl_->prefix_ = s; }
std::string  container::link_prefix() { return impl_->prefix_; }

task& container::schedule(int delay, handler *h) { return impl_->schedule(delay, h); }

} // namespace proton
