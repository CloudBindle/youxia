/*
 * Copyright (C) 2014 CloudBindle
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.cloudbindle.youxia.listing;

/**
 *
 * @author dyuen
 */
public class ListingFactory {

    public static AbstractInstanceListing createOpenStackListing() {
        return new OpenStackJCloudsListing();
    }

    public static AbstractInstanceListing createAWSListing() {
        return new AwsListing();
    }

    public static AbstractInstanceListing createAzureListing() {
        return new AzureListing();
    }

}
